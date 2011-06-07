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
import java.util.List;

import net.sf.orcc.ir.Actor;
import net.sf.orcc.ir.ExprBinary;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.InstAssign;
import net.sf.orcc.ir.InstCall;
import net.sf.orcc.ir.IrFactory;
import net.sf.orcc.ir.NodeBlock;
import net.sf.orcc.ir.NodeIf;
import net.sf.orcc.ir.NodeWhile;
import net.sf.orcc.ir.OpBinary;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.Type;
import net.sf.orcc.ir.Var;
import net.sf.orcc.ir.impl.IrFactoryImpl;
import net.sf.orcc.ir.util.AbstractActorVisitor;
import net.sf.orcc.ir.util.EcoreHelper;

/**
 * This class defines a visitor that transforms a division into an equivalent
 * hardware compilable function specified in xilinx division model
 * 
 * @author Khaled Jerbi
 * 
 */
public class DivisionSubstitution extends AbstractActorVisitor<Object> {
	public DivisionSubstitution() {
		super(true);
	}

	private int counter;
	private Procedure divProc = IrFactory.eINSTANCE.createProcedure("DIV_II",
			0, IrFactory.eINSTANCE.createTypeInt());

	@Override
	public Object caseActor(Actor actor) {
		boolean flagAdd = true;
		DivisionSearcher divisionSearcher = new DivisionSearcher(divProc,
				flagAdd);
		divisionSearcher.doSwitch(actor);
		// addition of the new div function once per actor
		if (!actor.getProcs().contains(divProc)) {
			actor.getProcs().add(divProc);
		}
		return null;
	}

	/**
	 * This class searches the div operators and transforms them into call
	 * instructions
	 * 
	 * @author Khaled Jerbi
	 * 
	 */
	private class DivisionSearcher extends AbstractActorVisitor<Object> {
		public DivisionSearcher(Procedure divProc, boolean flagAdd) {
			super(true);
			this.divProc = divProc;
			this.flagAdd = flagAdd;
		}

		private Procedure divProc;
		private boolean flagAdd;
		private Type typeInt = IrFactory.eINSTANCE.createTypeInt();
		private Type typeBool = IrFactory.eINSTANCE.createTypeBool();
		private List<Expression> parameters;

		@Override
		public Object caseExprBinary(ExprBinary expr) {
			parameters = new ArrayList<Expression>();
			if (expr.getOp() == OpBinary.DIV) {
				// what ever the epression type of division operands they are
				// put in local variables VarNum and varDenum the result of
				// callInst is put in tmp
				Var varNum = IrFactory.eINSTANCE.createVar(typeInt, "num",
						true, counter);
				Var varDenum = IrFactory.eINSTANCE.createVar(typeInt, "den",
						true, counter);
				Var tmp = IrFactory.eINSTANCE.createVar(typeInt, "tmpDiv", true,
						counter);
				counter++;

				InstAssign assign0 = IrFactory.eINSTANCE.createInstAssign(
						varNum, expr.getE1());
				InstAssign assign1 = IrFactory.eINSTANCE.createInstAssign(
						varDenum, expr.getE2());
				if (flagAdd) {
					divProc = createDivProc();
					flagAdd = false;
				}
				
				parameters.add(IrFactory.eINSTANCE.createExprVar(varNum));
				parameters.add(IrFactory.eINSTANCE.createExprVar(varDenum));
				
				procedure.getLocals().add(varNum);
				procedure.getLocals().add(varDenum);
				procedure.getLocals().add(tmp);
				
				InstCall call = IrFactory.eINSTANCE.createInstCall(tmp,
						divProc, parameters);
				EcoreHelper.addInstBeforeExpr(expr, assign0, false);
				EcoreHelper.addInstBeforeExpr(expr, assign1, false);
				EcoreHelper.addInstBeforeExpr(expr, call, false);
				expr.setE1(IrFactory.eINSTANCE.createExprVar(tmp));
				expr.setE2(IrFactory.eINSTANCE.createExprInt(0));
				expr.setOp(OpBinary.PLUS);
			}
			return null;
		}

		/**
		 * This method creates the alternative division function using the num
		 * and the denom
		 * 
		 * @param varNum
		 *            numerator
		 * @param varDenum
		 *            denomerator
		 * @return division function
		 */
		private Procedure createDivProc() {

			Var varNum = IrFactory.eINSTANCE.createVar(typeInt, "num", true, 0);
			Var varDenum = IrFactory.eINSTANCE.createVar(typeInt, "den", true, 0);
			// counter++;
			divProc.getParameters().add(varNum);
			divProc.getParameters().add(varDenum);

			Var result = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(), "result");
			Var i = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(), "i");
			Var flipResult = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(), "flipResult");
			Var denom = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(64), "denom");
			Var numer = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(64), "numer");
			Var mask = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(), "mask");
			Var remainder = divProc.newTempLocalVariable(
					IrFactory.eINSTANCE.createTypeInt(), "remainder");

			NodeBlock initBlock = createInitBlock(result, flipResult);
			divProc.getNodes().add(initBlock);

			NodeIf nodeIf_1 = createNodeIf(varNum, flipResult);
			divProc.getNodes().add(nodeIf_1);

			NodeIf nodeIf_2 = createNodeIf(varDenum, flipResult);
			divProc.getNodes().add(nodeIf_2);

			NodeBlock block_1 = IrFactoryImpl.eINSTANCE.createNodeBlock();
			InstAssign assign_blk10 = IrFactory.eINSTANCE.createInstAssign(
					remainder, IrFactory.eINSTANCE.createExprVar(varNum));
			Expression blk11And = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(varDenum),
					OpBinary.BITAND,
					IrFactory.eINSTANCE.createExprInt(0xFFFFFFFFL),
					IrFactory.eINSTANCE.createTypeInt());
			InstAssign assign_blk11 = IrFactory.eINSTANCE.createInstAssign(
					denom, blk11And);
			InstAssign assign_blk12 = IrFactory.eINSTANCE.createInstAssign(
					mask, IrFactory.eINSTANCE.createExprInt(0x80000000L));
			InstAssign assign_blk13 = IrFactory.eINSTANCE.createInstAssign(i,
					IrFactory.eINSTANCE.createExprInt(0));
			block_1.add(assign_blk10);
			block_1.add(assign_blk11);
			block_1.add(assign_blk12);
			block_1.add(assign_blk13);
			divProc.getNodes().add(block_1);

			NodeWhile nodeWhile = createNodeWhile(i, numer, remainder, denom,
					result, mask, varDenum);
			divProc.getNodes().add(nodeWhile);
			NodeIf nodeIf_3 = createResultNodeIf(flipResult, result);
			divProc.getNodes().add(nodeIf_3);

			NodeBlock blockReturn = IrFactoryImpl.eINSTANCE.createNodeBlock();
			blockReturn
					.add(IrFactory.eINSTANCE
							.createInstReturn(IrFactory.eINSTANCE
									.createExprVar(result)));
			divProc.getNodes().add(blockReturn);

			return divProc;
		}

		private NodeBlock createInitBlock(Var result, Var flipResult) {
			NodeBlock initBlock = IrFactoryImpl.eINSTANCE.createNodeBlock();
			InstAssign initResult = IrFactory.eINSTANCE.createInstAssign(
					result, IrFactory.eINSTANCE.createExprInt(0));
			InstAssign initFlip = IrFactory.eINSTANCE.createInstAssign(
					flipResult, IrFactory.eINSTANCE.createExprInt(0));
			initBlock.add(initResult);
			initBlock.add(initFlip);
			return initBlock;
		}

		/**
		 * creates the following if node: if (var < 0) { var = -var; flip ^= 1;
		 * }
		 * 
		 * @param var
		 *            (see definition)
		 * @param flip
		 *            (see definition)
		 * @return if node
		 */
		private NodeIf createNodeIf(Var var, Var flip) {
			NodeIf nodeIf = IrFactoryImpl.eINSTANCE.createNodeIf();
			NodeBlock blockIf_1 = IrFactoryImpl.eINSTANCE.createNodeBlock();
			Expression conditionIf_1 = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(var), OpBinary.LT,
					IrFactory.eINSTANCE.createExprInt(0), typeBool);
			nodeIf.setCondition(conditionIf_1);
			Expression oppNomerator = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprInt(0), OpBinary.MINUS,
					IrFactory.eINSTANCE.createExprVar(var), typeInt);
			InstAssign assign10 = IrFactory.eINSTANCE.createInstAssign(var,
					oppNomerator);
			blockIf_1.add(assign10);
			Expression xorFlip = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(flip), OpBinary.BITXOR,
					IrFactory.eINSTANCE.createExprInt(1), typeInt);
			InstAssign assign11 = IrFactory.eINSTANCE.createInstAssign(flip,
					xorFlip);
			blockIf_1.add(assign11);
			nodeIf.getThenNodes().add(blockIf_1);
			return nodeIf;
		}

		/**
		 * creates the required if node specified in the xilinx division model
		 * 
		 * @param numer
		 * @param denom
		 * @param result
		 * @param mask
		 * @param remainder
		 * @param varDenum
		 * @param i
		 * @return if Node
		 */
		private NodeIf createNodeIfWhile(Var numer, Var denom, Var result,
				Var mask, Var remainder, Var varDenum, Var i) {
			NodeIf nodeIf = IrFactory.eINSTANCE.createNodeIf();
			Expression condition = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(numer), OpBinary.GE,
					IrFactory.eINSTANCE.createExprVar(denom),
					IrFactory.eINSTANCE.createTypeInt());
			nodeIf.setCondition(condition);

			NodeBlock nodeBlk = IrFactory.eINSTANCE.createNodeBlock();
			Expression orExpr = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(result), OpBinary.BITOR,
					IrFactory.eINSTANCE.createExprVar(mask),
					IrFactory.eINSTANCE.createTypeInt());
			InstAssign assignBlk_0 = IrFactory.eINSTANCE.createInstAssign(
					result, orExpr);
			nodeBlk.add(assignBlk_0);

			Expression minusExpr = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprInt(31), OpBinary.MINUS,
					IrFactory.eINSTANCE.createExprVar(i),
					IrFactory.eINSTANCE.createTypeInt());
			Expression lShiftExpr = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(varDenum),
					OpBinary.SHIFT_LEFT, minusExpr,
					IrFactory.eINSTANCE.createTypeInt());
			Expression RemainderMinus = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(remainder),
					OpBinary.MINUS, lShiftExpr,
					IrFactory.eINSTANCE.createTypeInt());
			InstAssign assignBlk_1 = IrFactory.eINSTANCE.createInstAssign(
					remainder, RemainderMinus);
			nodeBlk.add(assignBlk_1);

			nodeIf.getThenNodes().add(nodeBlk);

			return nodeIf;
		}

		/**
		 * returns the required while node Specified in the xilinx division
		 * model
		 * 
		 * @param i
		 * @param numer
		 * @param remainder
		 * @param denom
		 * @param result
		 * @param mask
		 * @param varDenum
		 * @return
		 */
		private NodeWhile createNodeWhile(Var i, Var numer, Var remainder,
				Var denom, Var result, Var mask, Var varDenum) {
			NodeWhile nodeWhile = IrFactoryImpl.eINSTANCE.createNodeWhile();
			Expression condition = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(i), OpBinary.LT,
					IrFactory.eINSTANCE.createExprInt(32),
					IrFactory.eINSTANCE.createTypeBool());
			nodeWhile.setCondition(condition);

			NodeBlock nodeBlk_0 = IrFactory.eINSTANCE.createNodeBlock();
			Expression andExpr = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(remainder),
					OpBinary.BITAND,
					IrFactory.eINSTANCE.createExprInt(0xFFFFFFFFL),
					IrFactory.eINSTANCE.createTypeInt());
			Expression minusExpr = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprInt(31), OpBinary.MINUS,
					IrFactory.eINSTANCE.createExprVar(i),
					IrFactory.eINSTANCE.createTypeInt());
			Expression shiftExpr = IrFactory.eINSTANCE.createExprBinary(
					andExpr, OpBinary.SHIFT_RIGHT, minusExpr,
					IrFactory.eINSTANCE.createTypeInt());
			InstAssign assignBlk_0 = IrFactory.eINSTANCE.createInstAssign(
					numer, shiftExpr);
			nodeBlk_0.add(assignBlk_0);
			nodeWhile.getNodes().add(nodeBlk_0);

			NodeIf nodeIf = createNodeIfWhile(numer, denom, result, mask,
					remainder, varDenum, i);
			nodeWhile.getNodes().add(nodeIf);

			NodeBlock nodeBlk_1 = IrFactory.eINSTANCE.createNodeBlock();
			Expression maskShift = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(mask),
					OpBinary.SHIFT_RIGHT, IrFactory.eINSTANCE.createExprInt(1),
					IrFactory.eINSTANCE.createTypeInt());
			Expression assignBlk_1Value = IrFactory.eINSTANCE.createExprBinary(
					maskShift, OpBinary.BITAND,
					IrFactory.eINSTANCE.createExprInt(0x7FFFFFFFL),
					IrFactory.eINSTANCE.createTypeInt());
			InstAssign assignBlk_10 = IrFactory.eINSTANCE.createInstAssign(
					mask, assignBlk_1Value);
			nodeBlk_1.add(assignBlk_10);
			Expression iIncrement = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(i), OpBinary.PLUS,
					IrFactory.eINSTANCE.createExprInt(1),
					IrFactory.eINSTANCE.createTypeInt());
			InstAssign assignBlk_11 = IrFactory.eINSTANCE.createInstAssign(i,
					iIncrement);
			nodeBlk_1.add(assignBlk_11);
			nodeWhile.getNodes().add(nodeBlk_1);
			return nodeWhile;
		}

		/**
		 * returns an if node if (flipResult != 0) { result = -result; }
		 * 
		 * @param flipResult
		 *            (see definition)
		 * @param result
		 *            (see definition)
		 * @return If node (see definition)
		 */
		private NodeIf createResultNodeIf(Var flipResult, Var result) {
			NodeIf nodeIf = IrFactoryImpl.eINSTANCE.createNodeIf();
			NodeBlock blockIf_1 = IrFactoryImpl.eINSTANCE.createNodeBlock();
			Expression conditionIf = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprVar(flipResult), OpBinary.NE,
					IrFactory.eINSTANCE.createExprInt(0), typeBool);
			nodeIf.setCondition(conditionIf);
			Expression oppflip = IrFactory.eINSTANCE.createExprBinary(
					IrFactory.eINSTANCE.createExprInt(0), OpBinary.MINUS,
					IrFactory.eINSTANCE.createExprVar(result), typeInt);
			InstAssign assign10 = IrFactory.eINSTANCE.createInstAssign(result,
					oppflip);
			blockIf_1.add(assign10);
			nodeIf.getThenNodes().add(blockIf_1);
			return nodeIf;
		}
	}
}