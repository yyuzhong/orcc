package net.sf.orcc.tools.merger.actor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Pattern;
import net.sf.orcc.df.Port;
import net.sf.orcc.df.util.DfVisitor;
import net.sf.orcc.ir.BlockBasic;
import net.sf.orcc.ir.Def;
import net.sf.orcc.ir.ExprBinary;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.InstLoad;
import net.sf.orcc.ir.InstStore;
import net.sf.orcc.ir.IrFactory;
import net.sf.orcc.ir.OpBinary;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.Use;
import net.sf.orcc.ir.Var;
import net.sf.orcc.ir.util.AbstractIrVisitor;
import net.sf.orcc.ir.util.IrUtil;

public class ChangeFifoArrayAccess extends DfVisitor<Object> {

	private class IrVisitor extends AbstractIrVisitor<Object> {

		@Override
		public Object caseInstLoad(InstLoad load) {
			final IrFactory factory = IrFactory.eINSTANCE;
			Use use = load.getSource();
			Var var = use.getVariable();
			Port port = inputPattern.getVarToPortMap().get(var);
			if (port != null) {
				var = buffersMap.get(port);
				int cns = inputPattern.getNumTokens(port);
				loads.put(var, cns);
				use.setVariable(var);
				List<Expression> indexes = load.getIndexes();
				Expression e1 = factory
						.createExprVar(factory.createUse(superActor
								.getStateVar(var.getName() + "_r")));
				Expression e2 = IrUtil.copy(indexes.get(0));
				Expression bop = factory.createExprBinary(e1, OpBinary.PLUS,
						e2, e1.getType());
				indexes.set(0, bop);
			}

			return null;
		}

		@Override
		public Object caseInstStore(InstStore store) {
			final IrFactory factory = IrFactory.eINSTANCE;

			Def def = store.getTarget();
			Var var = def.getVariable();
			Port port = outputPattern.getVarToPortMap().get(var);
			if (port != null) {
				var = buffersMap.get(port);
				int prd = outputPattern.getNumTokens(port);
				stores.put(var, prd);
				def.setVariable(var);
				Expression e1 = factory
						.createExprVar(factory.createUse(superActor
								.getStateVar(var.getName() + "_w")));
				Expression e2 = IrUtil.copy(store.getIndexes().get(0));
				Expression bop = factory.createExprBinary(e1, OpBinary.PLUS,
						e2, e1.getType());
				store.getIndexes().set(0, bop);
				return null;
			}

			port = inputPattern.getVarToPortMap().get(var);
			if (port != null) {
				var = buffersMap.get(port);
				int cns = inputPattern.getNumTokens(port);
				stores.put(var, cns);
				def.setVariable(var);
				Expression e1 = factory
						.createExprVar(factory.createUse(superActor
								.getStateVar(var.getName() + "_r")));
				Expression e2 = IrUtil.copy(store.getIndexes().get(0));
				Expression bop = factory.createExprBinary(e1, OpBinary.PLUS,
						e2, e1.getType());
				store.getIndexes().set(0, bop);
				return null;
			}

			return null;
		}
	}

	private Map<Port, Var> buffersMap;
	private BlockBasic currentBlock;
	private Pattern inputPattern;

	private Map<Var, Integer> loads;

	private Pattern outputPattern;

	private Map<Var, Integer> stores;

	private Actor superActor;

	public ChangeFifoArrayAccess(Pattern inputPattern, Pattern outputPattern,
			Map<Port, Var> buffersMap) {
		this.inputPattern = inputPattern;
		this.outputPattern = outputPattern;
		this.buffersMap = buffersMap;
		
		irVisitor = new IrVisitor();
	}

	@Override
	public Object caseActor(Actor actor) {
		superActor = actor;
		for (Procedure proc : actor.getProcs()) {
			loads = new HashMap<Var, Integer>();
			stores = new HashMap<Var, Integer>();

			doSwitch(proc);

			currentBlock = proc.getLast();

			updateLoadIndex();
			updateStoreIndex();
		}
		return null;
	}

	private void updateLoadIndex() {
		for (Map.Entry<Var, Integer> entry : loads.entrySet()) {
			Var var = entry.getKey();
			int cns = entry.getValue();

			Var readVar = superActor.getStateVar(var.getName() + "_r");
			IrFactory factory = IrFactory.eINSTANCE;
			ExprBinary incr = factory.createExprBinary(
					factory.createExprVar(factory.createUse(readVar)),
					OpBinary.PLUS, factory.createExprInt(cns),
					readVar.getType());

			InstStore store = factory.createInstStore(readVar, incr);
			currentBlock.add(store);
		}
	}

	private void updateStoreIndex() {
		for (Map.Entry<Var, Integer> entry : stores.entrySet()) {
			Var var = entry.getKey();
			int prd = entry.getValue();

			Var readVar = superActor.getStateVar(var.getName() + "_w");
			IrFactory factory = IrFactory.eINSTANCE;
			ExprBinary incr = factory.createExprBinary(
					factory.createExprVar(factory.createUse(readVar)),
					OpBinary.PLUS, factory.createExprInt(prd),
					readVar.getType());

			InstStore store = factory.createInstStore(readVar, incr);
			currentBlock.add(store);
		}
	}

}