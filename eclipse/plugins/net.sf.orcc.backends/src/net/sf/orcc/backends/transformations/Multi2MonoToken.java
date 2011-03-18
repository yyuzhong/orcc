/*
 * Copyright (c) 2010, IETR/INSA of Rennes
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the IETR/INSA of Rennes nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package net.sf.orcc.backends.transformations;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Map.Entry;

import org.jgrapht.DirectedGraph;

import net.sf.orcc.ir.AbstractActorVisitor;
import net.sf.orcc.ir.Action;
import net.sf.orcc.ir.ActionScheduler;
import net.sf.orcc.ir.Actor;
import net.sf.orcc.ir.CFGNode;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.FSM;
import net.sf.orcc.ir.GlobalVariable;
import net.sf.orcc.ir.Instruction;
import net.sf.orcc.ir.IrFactory;
import net.sf.orcc.ir.LocalVariable;
import net.sf.orcc.ir.Location;
import net.sf.orcc.ir.Pattern;
import net.sf.orcc.ir.Port;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.Tag;
import net.sf.orcc.ir.Type;
import net.sf.orcc.ir.Use;
import net.sf.orcc.ir.FSM.State;
import net.sf.orcc.ir.expr.BinaryExpr;
import net.sf.orcc.ir.expr.BinaryOp;
import net.sf.orcc.ir.expr.BoolExpr;
import net.sf.orcc.ir.expr.IntExpr;
import net.sf.orcc.ir.expr.VarExpr;
import net.sf.orcc.ir.instructions.Assign;
import net.sf.orcc.ir.instructions.Load;
import net.sf.orcc.ir.instructions.Return;
import net.sf.orcc.ir.instructions.Store;
import net.sf.orcc.ir.nodes.BlockNode;
import net.sf.orcc.util.OrderedMap;
import net.sf.orcc.util.UniqueEdge;

/**
 * This class defines a visitor that transforms multi-token to mono-token data
 * transfer
 * 
 * @author Khaled Jerbi
 * 
 */
public class Multi2MonoToken extends AbstractActorVisitor {
	/**
	 * This class defines a visitor that substitutes the peek from the port to
	 * the new buffer and chnages the index from (index) to (index+writeIndex)
	 * 
	 * @author Khaled Jerbi
	 * 
	 */
	private class ModifyActionScheduler extends AbstractActorVisitor {

		private GlobalVariable buffer;
		private GlobalVariable writeIndex;

		// private Variable tempTab;

		public ModifyActionScheduler(GlobalVariable buffer,
				GlobalVariable writeIndex) {
			this.buffer = buffer;
			this.writeIndex = writeIndex;
		}

		@Override
		public void visit(Load load) {

			if (load.getSource().getVariable().getName().equals(port.getName())) {
				// change tab Name
				Use useArray = new Use(buffer);
				load.setSource(useArray);
				// change index --> writeIndex+index
				Expression expression1 = load.getIndexes().get(0);
				Expression expression2 = new VarExpr(new Use(writeIndex));
				Expression newExpression = new BinaryExpr(expression1,
						BinaryOp.PLUS, expression2, port.getType());
				load.getIndexes().set(0, newExpression);
			}
		}
	}

	/**
	 * This class defines a visitor that substitutes process variable names with
	 * those of the newly defined actions for Store
	 * 
	 * @author Khaled Jerbi
	 * 
	 */
	private class ModifyProcessActionStore extends AbstractActorVisitor {

		private GlobalVariable tab;

		// private Variable tempTab;

		public ModifyProcessActionStore(GlobalVariable tab) {
			this.tab = tab;
		}

		@Override
		public void visit(Load load) {

			if (load.getSource().getVariable().getName().equals(port.getName())) {
				Use useArray = new Use(tab);
				load.setSource(useArray);
			}
		}
	}

	/**
	 * This class defines a visitor that substitutes process variable names with
	 * those of the newly defined actions for write
	 * 
	 * @author Khaled JERBI
	 * 
	 */
	private class ModifyProcessActionWrite extends AbstractActorVisitor {

		private GlobalVariable tab;

		public ModifyProcessActionWrite(GlobalVariable tab) {
			this.tab = tab;
		}

		@Override
		public void visit(Store store) {
			if (store.getTarget().getName().equals(port.getName())) {
				store.setTarget(tab);
			}
		}
	}

	private Action done;

	private Type entryType;

	private FSM fsm;

	private int inputIndex = 0;

	private int numTokens;

	private int outputIndex = 0;

	private Port port;

	private Action process;

	private boolean repeatInput = false;

	private boolean repeatOutput = false;

	private LocalVariable result;

	private Action store;

	private Action untagged;

	private Action write;

	/**
	 * transforms the transformed action to a transition action
	 * 
	 * @param action
	 *            modified action
	 * @param buffer
	 *            current store buffer
	 * @param writeIndex
	 *            write index of the buffer
	 * @param readIndex
	 *            read index of the buffer
	 */
	private void actionToTransition(Action action, GlobalVariable buffer,
			GlobalVariable writeIndex, GlobalVariable readIndex) {
		ModifyActionScheduler modifyActionScheduler = new ModifyActionScheduler(
				buffer, writeIndex);
		modifyActionScheduler.visit(action.getScheduler());
		modifyActionSchedulability(writeIndex, readIndex);
	}

	/**
	 * this method changes the schedulability of the action accordingly to
	 * tokens disponibility in the buffer
	 * 
	 * @param writeIndex
	 *            write index of the buffer
	 * @param readIndex
	 *            read index of the buffer
	 */
	private void modifyActionSchedulability(GlobalVariable writeIndex,
			GlobalVariable readIndex) {
		BlockNode bodyNode = BlockNode.getFirst(action.getScheduler());
		LocalVariable localRead = new LocalVariable(true, 1, new Location(),
				"readIndex", IrFactory.eINSTANCE.createTypeInt(16));
		Instruction Load = new Load(localRead, new Use(readIndex));
		bodyNode.add(Load);

		LocalVariable localWrite = new LocalVariable(true, 1, new Location(),
				"writeIndex", IrFactory.eINSTANCE.createTypeInt(16));
		Instruction Load2 = new Load(localWrite, new Use(writeIndex));
		bodyNode.add(Load2);

		LocalVariable diff = new LocalVariable(true, 1, new Location(), "diff",
				IrFactory.eINSTANCE.createTypeInt(16));
		Expression value = new BinaryExpr(new VarExpr(new Use(readIndex)),
				BinaryOp.MINUS, new VarExpr(new Use(writeIndex)),
				IrFactory.eINSTANCE.createTypeInt(16));
		Instruction assign = new Assign(diff, value);
		bodyNode.add(assign);

		Expression condition = new BinaryExpr(new VarExpr(new Use(diff)),
				BinaryOp.GE, new IntExpr(numTokens),
				IrFactory.eINSTANCE.createTypeInt(16));

		Expression result = action.getScheduler().getResult();
		action.getScheduler().setResult(
				new BinaryExpr(result, BinaryOp.LOGIC_AND, condition,
						IrFactory.eINSTANCE.createTypeBool()));
	}

	/**
	 * Adds an FSM to an actor if it has not already
	 * 
	 */
	private void addFsm() {
		ActionScheduler scheduler = actor.getActionScheduler();

		fsm = new FSM();
		fsm.setInitialState("init");
		fsm.addState("init");
		for (Action action : scheduler.getActions()) {
			fsm.addTransition("init", action, "init");
		}

		scheduler.getActions().clear();
		scheduler.setFsm(fsm);
	}

	/**
	 * This method creates an action with the given name.
	 * 
	 * @param name
	 *            name of the action
	 * @return a new action created with the given name
	 */
	private Action createAction(Expression condition, String name) {
		// scheduler
		Procedure scheduler = new Procedure("isSchedulable_" + name,
				new Location(), IrFactory.eINSTANCE.createTypeBool());
		LocalVariable result = scheduler.newTempLocalVariable(
				this.actor.getFile(), IrFactory.eINSTANCE.createTypeBool(),
				"result");
		result.setIndex(1);
		scheduler.getLocals().remove(result.getBaseName());
		scheduler.getLocals().put(result.getName(), result);

		BlockNode block = new BlockNode(scheduler);
		block.add(new Assign(result, condition));
		block.add(new Return(new VarExpr(new Use(result))));
		scheduler.getNodes().add(block);

		// body
		Procedure body = new Procedure(name, new Location(),
				IrFactory.eINSTANCE.createTypeVoid());
		block = new BlockNode(body);
		block.add(new Return(null));
		body.getNodes().add(block);

		// tag
		Tag tag = new Tag();
		tag.add(name);

		Action action = new Action(new Location(), tag, new Pattern(),
				new Pattern(), scheduler, body);

		// add action to actor's actions
		this.actor.getActions().add(action);

		return action;
	}

	/**
	 * This method creates the required Store, done, untagged and process
	 * actions
	 * 
	 * @param action
	 *            the action getting transformed
	 */
	private void createActionsSet(Action action, String sourceName,
			String targetName) {
		scanInputs(action, sourceName, targetName);
		scanOutputs(action, sourceName, targetName);
	}

	/**
	 * This method creates a global variable counter for store with the given
	 * name.
	 * 
	 * @param name
	 *            name of the counter
	 * @return new counter with the given name
	 */
	private GlobalVariable createCounter(String name) {
		GlobalVariable newCounter = new GlobalVariable(new Location(),
				IrFactory.eINSTANCE.createTypeInt(16), name, true);
		Expression expression = new IntExpr(0);
		newCounter.setInitialValue(expression);
		if (!actor.getStateVars().contains(newCounter.getName())) {
			actor.getStateVars().put(newCounter.getName(), newCounter);
		}
		return newCounter;
	}

	/**
	 * This method creates the done action that is schedulable when required
	 * number of tokens is read (written)
	 * 
	 * @param actionName
	 *            name of the action
	 * @param counter
	 *            global variable counter used for reading (writing) tokens
	 * @param numTokens
	 *            repeat value
	 * @return new done action
	 */
	private Action createDoneAction(String name, GlobalVariable counter,
			int numTokens) {
		// body
		Procedure body = new Procedure(name, new Location(),
				IrFactory.eINSTANCE.createTypeVoid());
		BlockNode block = new BlockNode(body);
		Store store = new Store(counter, new IntExpr(0));
		block.add(store);
		block.add(new Return(null));
		body.getNodes().add(block);

		// scheduler
		Procedure scheduler = new Procedure("isSchedulable_" + name,
				new Location(), IrFactory.eINSTANCE.createTypeBool());
		LocalVariable temp = scheduler.newTempLocalVariable(
				this.actor.getFile(), IrFactory.eINSTANCE.createTypeBool(),
				"temp");
		temp.setIndex(1);
		scheduler.getLocals().remove(temp.getBaseName());
		scheduler.getLocals().put(temp.getName(), temp);
		result = new LocalVariable(true, 0, new Location(), "result",
				IrFactory.eINSTANCE.createTypeBool());
		scheduler.getLocals().put(result.getName(), result);
		LocalVariable localCounter = new LocalVariable(true, 1, new Location(),
				"localCounter", counter.getType());
		scheduler.getLocals().put(localCounter.getName(), localCounter);
		block = new BlockNode(scheduler);
		Load schedulerLoad = new Load(localCounter, new Use(counter));
		block.add(0, schedulerLoad);

		Expression guardValue = new IntExpr(numTokens);
		Expression counterExpression = new VarExpr(new Use(localCounter));
		Expression expression = new BinaryExpr(counterExpression, BinaryOp.EQ,
				guardValue, IrFactory.eINSTANCE.createTypeBool());
		block.add(new Assign(temp, expression));
		block.add(new Assign(result, new VarExpr(new Use(temp))));
		block.add(new Return(new VarExpr(new Use(result))));
		scheduler.getNodes().add(block);

		// tag
		Tag tag = new Tag();
		tag.add(name);

		Action action = new Action(new Location(), tag, new Pattern(),
				new Pattern(), scheduler, body);

		// add action to actor's actions
		this.actor.getActions().add(action);

		return action;
	}

	/**
	 * This method creates the process action using the nodes & locals of the
	 * action getting transformed
	 * 
	 * @param action
	 *            currently transforming action
	 * @return new process action
	 */
	private Action createProcessAction(Action action) {
		Expression expression = new BoolExpr(true);
		Action newProcessAction = createAction(expression, "newProcess_"
				+ action.getName());
		Procedure body = newProcessAction.getBody();

		ListIterator<CFGNode> listIt = action.getBody().getNodes()
				.listIterator();
		moveNodes(listIt, body);
		Iterator<LocalVariable> it = action.getBody().getLocals().iterator();
		moveLocals(it, body);
		if (repeatOutput && !repeatInput) {
			Procedure scheduler = newProcessAction.getScheduler();
			listIt = action.getScheduler().getNodes().listIterator();
			moveNodes(listIt, scheduler);
			it = action.getScheduler().getLocals().iterator();
			moveLocals(it, scheduler);
		}
		return newProcessAction;
	}

	/**
	 * This method defines a new store action that reads 1 token on the repeat
	 * port
	 * 
	 * @param actionName
	 *            name of the new store action
	 * @param numTokens
	 *            repeat number
	 * @param port
	 *            repeat port
	 * @param readCounter
	 *            global variable counter
	 * @param storeList
	 *            global variable list of store (write)
	 * @return new store action
	 */
	private Action createStoreAction(String actionName,
			GlobalVariable readCounter, GlobalVariable storeList,
			GlobalVariable buffer, GlobalVariable writeIndex) {
		String storeName = actionName + port.getName() + "_NewStore";
		Expression guardValue = new IntExpr(numTokens);
		Expression counterExpression = new VarExpr(new Use(readCounter));
		Expression expression = new BinaryExpr(counterExpression, BinaryOp.LT,
				guardValue, IrFactory.eINSTANCE.createTypeBool());

		Action newStoreAction = createAction(expression, storeName);
		defineStoreBody(readCounter, storeList, newStoreAction.getBody(),
				buffer, writeIndex);
		return newStoreAction;
	}

	/**
	 * This method creates a global variable counter for data storing (writing)
	 * 
	 * @param name
	 *            name of the list
	 * @param numTokens
	 *            size of the list
	 * @param entryType
	 *            type of the list
	 * @return a global variable list
	 */
	private GlobalVariable createTab(String name, Type entryType, int size) {
		Type type = IrFactory.eINSTANCE.createTypeList(size, entryType);
		GlobalVariable newList = new GlobalVariable(new Location(), type, name,
				true);
		if (!actor.getStateVars().contains(newList.getName())) {
			actor.getStateVars().put(newList.getName(), newList);
		}
		return newList;
	}

	/**
	 * creates an untagged action to store tokens
	 * 
	 * @param storeCounter
	 *            global variable counter
	 * @param storeList
	 *            global variable list to store
	 * @return new untagged action
	 */
	private Action createUntaggedAction(GlobalVariable readIndex,
			GlobalVariable storeList) {
		Expression expression = new BoolExpr(true);
		Action newUntaggedAction = createAction(expression,
				"untagged_" + port.getName());
		LocalVariable localINPUT = new LocalVariable(true, 0, new Location(),
				port.getName(), IrFactory.eINSTANCE.createTypeList(1,
						port.getType()));
		defineUntaggedBody(readIndex, storeList, newUntaggedAction.getBody(),
				localINPUT);
		Pattern pattern = newUntaggedAction.getInputPattern();
		pattern.setNumTokens(port, 1);
		pattern.setVariable(port, localINPUT);
		return newUntaggedAction;
	}

	/**
	 * This method creates the new write action
	 * 
	 * @param actionName
	 *            action name
	 * @param writeCounter
	 *            global variable write counter
	 * @param writeList
	 *            global variable write list
	 * @return new write action
	 */
	private Action createWriteAction(String actionName,
			GlobalVariable writeCounter, GlobalVariable writeList) {
		String writeName = actionName + port.getName() + "_NewWrite";
		Expression guardValue = new IntExpr(numTokens);
		Expression counterExpression = new VarExpr(new Use(writeCounter));
		Expression expression = new BinaryExpr(counterExpression, BinaryOp.LT,
				guardValue, IrFactory.eINSTANCE.createTypeBool());
		Action newWriteAction = createAction(expression, writeName);

		LocalVariable OUTPUT = new LocalVariable(true, 0, new Location(),
				port.getName() + "OUTPUT", IrFactory.eINSTANCE.createTypeList(
						1, port.getType()));
		defineWriteBody(writeCounter, writeList, newWriteAction.getBody(),
				OUTPUT);
		// add output pattern
		Pattern pattern = newWriteAction.getOutputPattern();
		pattern.setNumTokens(port, 1);
		pattern.setVariable(port, OUTPUT);
		return newWriteAction;
	}

	/**
	 * This method creates the instructions for the body of the new store action
	 * 
	 * @param port
	 *            repeat port
	 * @param readCounter
	 *            global variable counter
	 * @param storeList
	 *            global store (write) list
	 * @param body
	 *            new store action body
	 */
	private void defineStoreBody(GlobalVariable readCounter,
			GlobalVariable storeList, Procedure body, GlobalVariable buffer,
			GlobalVariable writeIndex) {
		BlockNode bodyNode = BlockNode.getFirst(body);

		OrderedMap<String, LocalVariable> locals = body.getLocals();
		LocalVariable counter = new LocalVariable(true, 1, new Location(),
				port.getName() + "_Local_counter", readCounter.getType());
		locals.put(counter.getName(), counter);
		Instruction load1 = new Load(counter, new Use(readCounter));
		bodyNode.add(load1);

		LocalVariable index = new LocalVariable(true, 1, new Location(),
				"writeIndex", IrFactory.eINSTANCE.createTypeInt(16));
		locals.put(index.getName(), index);
		Instruction loadIndex = new Load(index, new Use(writeIndex));
		bodyNode.add(loadIndex);

		LocalVariable input = new LocalVariable(true, 1, new Location(),
				port.getName() + "_Input", port.getType());
		locals.put(input.getName(), input);
		List<Expression> load2Index = new ArrayList<Expression>(1);
		Expression expression1 = new VarExpr(new Use(counter));
		Expression expression2 = new IntExpr(511);
		Expression indexMask = new BinaryExpr(expression1, BinaryOp.BITAND,
				expression2, readCounter.getType());
		// mask index
		load2Index.add(indexMask);
		Instruction load2 = new Load(input, new Use(buffer), load2Index);
		bodyNode.add(load2);

		List<Expression> store1Index = new ArrayList<Expression>(1);
		store1Index.add(new VarExpr(new Use(counter)));
		Instruction store1 = new Store(storeList, store1Index, new VarExpr(
				new Use(input)));
		bodyNode.add(store1);

		// globalCounter=GlobalCounter+1
		LocalVariable counter2 = new LocalVariable(true, 2, new Location(),
				port.getName() + "_Local_counter", readCounter.getType());
		locals.put(counter2.getName(), counter2);
		Expression storeIndexElement = new VarExpr(new Use(counter));
		Expression e2 = new IntExpr(1);
		Expression assignValue = new BinaryExpr(storeIndexElement,
				BinaryOp.PLUS, e2, entryType);
		Instruction assign = new Assign(counter2, assignValue);
		bodyNode.add(assign);
		Instruction store2 = new Store(readCounter, new VarExpr(new Use(
				counter2)));
		bodyNode.add(store2);

		// writeIndex = (writeIndex&511) +1
		LocalVariable index2 = new LocalVariable(true, 2, new Location(),
				"indexTemp", IrFactory.eINSTANCE.createTypeInt(16));
		Expression value = new BinaryExpr(indexMask, BinaryOp.PLUS, e2,
				entryType);
		Instruction assign2 = new Assign(index2, value);
		bodyNode.add(assign2);
	}

	/**
	 * This method creates the instructions for the body of the new untagged
	 * action
	 * 
	 * @param port
	 *            repeat port
	 * @param readCounter
	 *            global variable counter
	 * @param storeList
	 *            global store list
	 * @param body
	 *            new untagged action body
	 */
	private void defineUntaggedBody(GlobalVariable readCounter,
			GlobalVariable storeList, Procedure body, LocalVariable localINPUT) {
		BlockNode bodyNode = BlockNode.getFirst(body);

		OrderedMap<String, LocalVariable> locals = body.getLocals();
		LocalVariable counter = new LocalVariable(true, 1, new Location(),
				port.getName() + "_Local_counter", readCounter.getType());
		locals.put(counter.getName(), counter);
		Use readCounterUse = new Use(readCounter);
		Instruction load1 = new Load(counter, readCounterUse);
		bodyNode.add(load1);

		LocalVariable input = new LocalVariable(true, 1, new Location(),
				port.getName() + "_Input", port.getType());
		locals.put(input.getName(), input);
		List<Expression> load2Index = new ArrayList<Expression>(1);
		load2Index.add(new IntExpr(0));
		Instruction load2 = new Load(input, new Use(localINPUT), load2Index);
		bodyNode.add(load2);

		List<Expression> store1Index = new ArrayList<Expression>(1);
		Expression expression1 = new VarExpr(new Use(counter));
		// index mask (index & 511)
		Expression expression2 = new IntExpr(511);
		Expression indexMask = new BinaryExpr(expression1, BinaryOp.BITAND,
				expression2, readCounter.getType());
		store1Index.add(indexMask);
		Instruction store1 = new Store(storeList, store1Index, new VarExpr(
				new Use(input)));
		bodyNode.add(store1);
		// globalCounter = GlobalCounter+1
		LocalVariable counter2 = new LocalVariable(true, 2, new Location(),
				port.getName() + "_Local_counter", readCounter.getType());
		locals.put(counter2.getName(), counter2);
		Expression storeIndexElement = new VarExpr(new Use(counter));
		Expression e2 = new IntExpr(1);
		Expression assignValue = new BinaryExpr(storeIndexElement,
				BinaryOp.PLUS, e2, entryType);
		Instruction assign = new Assign(counter2, assignValue);
		bodyNode.add(assign);

		Instruction store2 = new Store(readCounter, new VarExpr(new Use(
				counter2)));
		bodyNode.add(store2);
		// readIndex = (readIndex & 511) + 1
		LocalVariable index2 = new LocalVariable(true, 2, new Location(),
				"indexTemp", IrFactory.eINSTANCE.createTypeInt(16));
		Expression value = new BinaryExpr(indexMask, BinaryOp.PLUS, e2,
				entryType);
		Instruction assign2 = new Assign(index2, value);
		bodyNode.add(assign2);

	}

	/**
	 * This method defines the instructions of the new write action body
	 * 
	 * @param writeCounter
	 *            global variable counter
	 * @param writeList
	 *            global variable list for write
	 * @param body
	 *            body of the new write action
	 */

	private void defineWriteBody(GlobalVariable writeCounter,
			GlobalVariable writeList, Procedure body, LocalVariable OUTPUT) {
		BlockNode bodyNode = BlockNode.getFirst(body);
		OrderedMap<String, LocalVariable> locals = body.getLocals();
		LocalVariable counter1 = new LocalVariable(true, outputIndex,
				new Location(), port.getName() + "_Local_writeCounter",
				writeCounter.getType());
		locals.put(counter1.getName(), counter1);
		Use writeCounterUse = new Use(writeCounter);
		Instruction load1 = new Load(counter1, writeCounterUse);
		bodyNode.add(load1);

		LocalVariable output = new LocalVariable(true, outputIndex,
				new Location(), port.getName() + "_LocalOutput", port.getType());
		locals.put(output.getName(), output);
		List<Expression> load2Index = new ArrayList<Expression>(1);
		load2Index.add(new VarExpr(writeCounterUse));
		Instruction load2 = new Load(output, new Use(writeList), load2Index);
		bodyNode.add(load2);

		LocalVariable out = new LocalVariable(true, outputIndex,
				new Location(), "_LocalTemp", port.getType());
		locals.put(out.getName(), out);
		Use assign1Expr = new Use(output);
		Expression assign1Value = new VarExpr(assign1Expr);
		Instruction assign1 = new Assign(out, assign1Value);
		bodyNode.add(assign1);

		LocalVariable counter2 = new LocalVariable(true, outputIndex,
				new Location(), port.getName() + "_Local_writeCounter_2",
				writeCounter.getType());
		locals.put(counter2.getName(), counter2);
		Expression assign2IndexElement = new VarExpr(new Use(counter1));
		Expression e2Assign2 = new IntExpr(1);
		Expression assign2Value = new BinaryExpr(assign2IndexElement,
				BinaryOp.PLUS, e2Assign2, IrFactory.eINSTANCE.createTypeInt(16));
		Instruction assign2 = new Assign(counter2, assign2Value);
		bodyNode.add(assign2);

		// locals.put(OUTPUT.getName(), OUTPUT);
		VarExpr store1Expression = new VarExpr(new Use(out));
		List<Expression> store1Index = new ArrayList<Expression>(1);
		store1Index.add(new IntExpr(0));
		Instruction store1 = new Store(OUTPUT, store1Index, store1Expression);
		bodyNode.add(store1);

		Expression store2Expression = new VarExpr(new Use(counter2));
		Instruction store2 = new Store(writeCounter, store2Expression);
		bodyNode.add(store2);
	}

	/**
	 * This method changes the schedulability of the done action
	 * 
	 * @param counter
	 *            Global Variable counter
	 */
	private void modifyDoneAction(GlobalVariable counter, int portIndex) {

		BlockNode blkNode = BlockNode.getFirst(done.getBody());
		Expression storeValue = new IntExpr(0);
		Instruction store = new Store(counter, storeValue);
		blkNode.add(store);

		blkNode = BlockNode.getFirst(done.getScheduler());
		OrderedMap<String, LocalVariable> schedulerLocals = done.getScheduler()
				.getLocals();
		LocalVariable localCounter = new LocalVariable(true, portIndex,
				new Location(), "localCounterModif", counter.getType());
		schedulerLocals.put(localCounter.getName(), localCounter);

		Instruction load = new Load(localCounter, new Use(counter));
		blkNode.add(1, load);

		LocalVariable temp = new LocalVariable(true, portIndex, new Location(),
				"temp", IrFactory.eINSTANCE.createTypeBool());
		schedulerLocals.put(temp.getName(), temp);
		Expression guardValue = new IntExpr(numTokens);
		Expression counterExpression = new VarExpr(new Use(localCounter));
		Expression schedulerValue = new BinaryExpr(counterExpression,
				BinaryOp.EQ, guardValue, IrFactory.eINSTANCE.createTypeBool());
		Instruction assign = new Assign(temp, schedulerValue);
		int index = blkNode.getInstructions().size() - 1;
		blkNode.add(index, assign);
		index++;

		Expression buffrerExpression = new VarExpr(new Use(result));
		Expression resultExpression = new VarExpr(new Use(temp));
		Expression expression = new BinaryExpr(buffrerExpression,
				BinaryOp.LOGIC_AND, resultExpression,
				IrFactory.eINSTANCE.createTypeBool());
		Instruction bufferAssign = new Assign(result, expression);
		blkNode.add(index, bufferAssign);

	}

	/**
	 * This method moves the local variables of a procedure to another using a
	 * LocalVariable iterator
	 * 
	 * @param itVar
	 *            source LocalVariable iterator
	 * @param newProc
	 *            target procedure
	 */
	private void moveLocals(Iterator<LocalVariable> itVar, Procedure newProc) {
		while (itVar.hasNext()) {
			LocalVariable var = itVar.next();
			itVar.remove();
			newProc.getLocals().put(var.getName(), var);
		}
	}

	/**
	 * This method moves the nodes of a procedure to another using a CFGNode
	 * iterator
	 * 
	 * @param itNode
	 *            source node iterator
	 * @param newProc
	 *            target procedure
	 */
	private void moveNodes(ListIterator<CFGNode> itNode, Procedure newProc) {
		while (itNode.hasNext()) {
			CFGNode node = itNode.next();
			itNode.remove();
			newProc.getNodes().add(node);
		}
	}

	/**
	 * This method clones the output patterns from a source action to a target
	 * one
	 * 
	 * @param source
	 *            source action
	 * @param target
	 *            target action
	 */
	private void moveOutputPattern(Action source, Action target) {
		Pattern targetPattern = target.getOutputPattern();
		Pattern sourcePattern = source.getOutputPattern();
		targetPattern.getNumTokensMap().putAll(sourcePattern.getNumTokensMap());
		targetPattern.getPorts().addAll(sourcePattern.getPorts());
		targetPattern.getVariableMap().putAll(sourcePattern.getVariableMap());
		targetPattern.getInverseVariableMap().putAll(
				sourcePattern.getInverseVariableMap());
	}

	/**
	 * returns the position of a port in a port list
	 * 
	 * @param list
	 *            list of ports
	 * @param seekPort
	 *            researched port
	 * @return position of a port in a list
	 */
	private int portPosition(List<Port> list, Port seekPort) {
		int position = 0;
		for (Port inputPort : list) {
			if (inputPort == seekPort) {
				break;
			} else {
				position++;
			}
		}
		return position;
	}

	/**
	 * removes the local variables of a procedure
	 * 
	 * @param it
	 *            local variable iterator
	 * @param procedure
	 *            procedure containing the local variables to remove
	 */
	private void removeLocals(Procedure procedure) {
		Iterator<LocalVariable> it = procedure.getLocals().iterator();
		while (it.hasNext()) {
			it.remove();
		}
	}

	/**
	 * removes the nodes of a procedure
	 * 
	 * @param it
	 *            node iterator
	 * @param procedure
	 *            procedure containing the nodes to remove
	 */
	private void removeNodes(Procedure procedure) {
		Iterator<CFGNode> it = procedure.getNodes().iterator();
		while (it.hasNext()) {
			it.remove();
		}
	}

	/**
	 * removes the ports of an action's input pattern
	 * 
	 * @param action
	 *            action of the pattern to remove
	 */
	private void removePatternPorts(Action action) {
		Iterator<Port> it = action.getInputPattern().getPorts().iterator();
		while (it.hasNext()) {
			it.remove();
		}
	}

	/**
	 * For every Input of the action this method creates the new required
	 * actions
	 * 
	 * @param action
	 *            action to transform
	 * @param sourceName
	 *            name of the source state of the action in the actor fsm
	 * @param targetName
	 *            name of the target state of the action in the actor fsm
	 */
	private void scanInputs(Action action, String sourceName, String targetName) {
		for (Entry<Port, Integer> verifEntry : action.getInputPattern()
				.getNumTokensMap().entrySet()) {
			int verifNumTokens = verifEntry.getValue();
			if (verifNumTokens > 1) {
				repeatInput = true;
				// create new process action
				process = createProcessAction(action);
				String storeName = "newStateStore" + action.getName();
				String processName = "newStateProcess" + action.getName();
				fsm.addState(processName);
				fsm.addTransition(processName, process, targetName);
				// move action's Output pattern to new process action
				moveOutputPattern(action, process);
				// create a list to store the treated input ports
				List<Port> inputPorts = new ArrayList<Port>();
				List<GlobalVariable> inputBuffers = new ArrayList<GlobalVariable>();
				List<GlobalVariable> readIndexes = new ArrayList<GlobalVariable>();
				List<GlobalVariable> writeIndexes = new ArrayList<GlobalVariable>();
				GlobalVariable untagBuffer = new GlobalVariable(new Location(),
						entryType, "buffer", true);
				GlobalVariable untagReadIndex = new GlobalVariable(
						new Location(), entryType, "index", true);
				GlobalVariable untagWriteIndex = new GlobalVariable(
						new Location(), entryType, "index", true);
				// if input repeat detected --> treat all input ports
				for (Entry<Port, Integer> entry : action.getInputPattern()
						.getNumTokensMap().entrySet()) {
					numTokens = entry.getValue();
					inputIndex = inputIndex + 1;
					port = entry.getKey();
					entryType = entry.getKey().getType();

					if (inputPorts.contains(port)) {
						int position = portPosition(inputPorts, port);
						untagBuffer = inputBuffers.get(position);
						untagReadIndex = readIndexes.get(position);
						untagWriteIndex = writeIndexes.get(position);
					} else {
						inputPorts.add(port);
						untagBuffer = createTab(port.getName() + "_buffer",
								entryType, 512);
						untagReadIndex = createCounter("readIndex_"
								+ port.getName());
						untagWriteIndex = createCounter("writeIndex_"
								+ port.getName());
						untagged = createUntaggedAction(untagReadIndex,
								untagBuffer);
						actor.getActions().add(untagged);
						actor.getActionScheduler().getActions().add(untagged);
					}
					String counterName = action.getName() + "NewStoreCounter"
							+ inputIndex;
					GlobalVariable counter = createCounter(counterName);
					String listName = action.getName() + "NewStoreList"
							+ inputIndex;
					GlobalVariable tab = createTab(listName, entryType,
							numTokens);
					store = createStoreAction(action.getName(), counter, tab,
							untagBuffer, untagWriteIndex);
					ModifyProcessActionStore modifyProcessAction = new ModifyProcessActionStore(
							tab);
					modifyProcessAction.visit(process.getBody());
					fsm.addState(storeName);
					fsm.addTransition(storeName, store, storeName);

					// create a new store done action once
					if (inputIndex == 1) {
						done = createDoneAction(action.getName()
								+ "newStoreDone", counter, numTokens);
						fsm.addTransition(storeName, done, processName);
					} else {
						// the new done action already exists --> modify
						// schedulability
						modifyDoneAction(counter, inputIndex);
					}
					actionToTransition(action, untagBuffer, untagWriteIndex,
							untagReadIndex);
				}
				// change the transformed action to a transition action to keep
				// the same fireability order
				removePatternPorts(action);
				removeNodes(action.getBody());
				removeLocals(action.getBody());
				fsm.replaceTarget(sourceName, action, storeName);

				break;
			}
		}
		inputIndex = 0;
	}

	/**
	 * For every output of the action this method creates the new required
	 * actions
	 * 
	 * @param action
	 *            action to transform
	 * @param sourceName
	 *            name of the source state of the action in the actor fsm
	 * @param targetName
	 *            name of the target state of the action in the actor fsm
	 */
	private void scanOutputs(Action action, String sourceName, String targetName) {
		for (Entry<Port, Integer> verifEntry : action.getOutputPattern()
				.getNumTokensMap().entrySet()) {
			int verifNumTokens = verifEntry.getValue();
			if (verifNumTokens > 1) {
				repeatOutput = true;
				String processName = "newStateProcess" + action.getName();
				String writeName = "newStateWrite" + action.getName();

				// create new process action if not created while treating
				// inputs
				if (!repeatInput) {
					process = createProcessAction(action);
					fsm.addTransition(sourceName, process, writeName);
					this.actor.getActions().remove(action);
				} else {
					fsm.replaceTarget(processName, process, writeName);
					process.getOutputPattern().clear();
				}
				for (Entry<Port, Integer> entry : action.getOutputPattern()
						.getNumTokensMap().entrySet()) {
					numTokens = entry.getValue();
					outputIndex = outputIndex + 1;
					port = entry.getKey();
					entryType = entry.getKey().getType();
					String counterName = action.getName() + "NewWriteCounter"
							+ outputIndex;
					GlobalVariable counter = createCounter(counterName);
					String listName = action.getName() + "NewWriteList"
							+ outputIndex;
					GlobalVariable tab = createTab(listName, entryType,
							numTokens);
					write = createWriteAction(action.getName(), counter, tab);
					write.getOutputPattern().setNumTokens(port, 1);

					ModifyProcessActionWrite modifyProcessActionWrite = new ModifyProcessActionWrite(
							tab);
					modifyProcessActionWrite.visit(process.getBody());
					fsm.addState(writeName);
					fsm.addTransition(writeName, write, writeName);

					// create a new write done action once
					if (outputIndex == 1) {
						done = createDoneAction(action.getName()
								+ "newWriteDone", counter, numTokens);
						fsm.addTransition(writeName, done, targetName);
					} else {
						modifyDoneAction(counter, outputIndex);
					}
				}
				break;
			}
		}
		outputIndex = 0;
	}

	@Override
	public void visit(Actor actor) {
		this.actor = actor;
		fsm = actor.getActionScheduler().getFsm();
		List<Action> actions = new ArrayList<Action>(actor.getActionScheduler()
				.getActions());

		if (fsm == null) {
			// no FSM: simply visit all the actions
			addFsm();
			for (Action action : actions) {
				String sourceName = "init";
				String targetName = "init";
				visitTransition(sourceName, targetName, action);
			}
		} else {
			// with an FSM: visits all transitions
			DirectedGraph<State, UniqueEdge> graph = fsm.getGraph();
			Set<UniqueEdge> edges = graph.edgeSet();
			for (UniqueEdge edge : edges) {
				State source = graph.getEdgeSource(edge);
				String sourceName = source.getName();

				State target = graph.getEdgeTarget(edge);
				String targetName = target.getName();

				Action action = (Action) edge.getObject();
				visitTransition(sourceName, targetName, action);
			}
		}
	}

	/**
	 * visits a transition characterized by its source name, target name and
	 * action
	 * 
	 * @param sourceName
	 *            source state
	 * @param targetName
	 *            target state
	 * @param action
	 *            action of the transition
	 */
	private void visitTransition(String sourceName, String targetName,
			Action action) {
		createActionsSet(action, sourceName, targetName);
		if (repeatInput) {
			fsm.removeTransition(sourceName, action);
			repeatInput = false;
		} else {
			if (repeatOutput) {
				fsm.removeTransition(sourceName, action);
				repeatOutput = false;
			}
		}

	}

}