/*
 * Copyright (c) 2009, IETR/INSA of Rennes
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
package net.sf.orcc.backends.llvm.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.sf.orcc.ir.Location;
import net.sf.orcc.ir.VarDef;
import net.sf.orcc.ir.actor.VarUse;
import net.sf.orcc.ir.actor.Action;
import net.sf.orcc.ir.actor.Actor;
import net.sf.orcc.ir.actor.Procedure;
import net.sf.orcc.ir.nodes.AbstractNode;
import net.sf.orcc.ir.nodes.AbstractNodeVisitor;
import net.sf.orcc.ir.nodes.IfNode;
import net.sf.orcc.ir.nodes.StoreNode;
import net.sf.orcc.ir.nodes.ReadNode;
import net.sf.orcc.ir.nodes.ReturnNode;
import net.sf.orcc.ir.nodes.LoadNode;
import net.sf.orcc.ir.nodes.JoinNode;
import net.sf.orcc.ir.nodes.EmptyNode;
import net.sf.orcc.ir.nodes.AssignVarNode;
import net.sf.orcc.backends.llvm.nodes.LabelNode;
import net.sf.orcc.ir.expr.AbstractExpr;
import net.sf.orcc.ir.expr.IntExpr;
import net.sf.orcc.ir.expr.VarExpr;
import net.sf.orcc.ir.type.VoidType;
import net.sf.orcc.backends.llvm.nodes.LoadFifo;
import net.sf.orcc.backends.llvm.nodes.BrNode;
import net.sf.orcc.backends.llvm.nodes.BrLabelNode;
import net.sf.orcc.ir.expr.TypeExpr;
import net.sf.orcc.ir.actor.Procedure;

/**
 * Move writes to the beginning of an action (because we use pointers).
 * 
 * @author J�r�me GORIN
 * 
 */
public class ControlFlowTransformation extends AbstractNodeVisitor {

	private int BrCounter;
	
	public ControlFlowTransformation(Actor actor) {
		for (Procedure proc : actor.getProcs()) {
			visitProc(proc);
		}

		for (Action action : actor.getActions()) {
			visitProc(action.getBody());
			visitProc(action.getScheduler());
		}

		for (Action action : actor.getInitializes()) {
			visitProc(action.getBody());
			visitProc(action.getScheduler());
		}
	}

	@Override
	public void visit(IfNode node, Object... args) {
		ListIterator<AbstractNode> it = (ListIterator<AbstractNode>) args[0];
		List<AbstractNode> thenNodes = node.getThenNodes();
		List<AbstractNode> elseNodes = node.getElseNodes();
		
		
		if (thenNodes.isEmpty()&&elseNodes.isEmpty()){
			SelectNodeCreate(node);
		} else { 
			BrNode brNode = BrNodeCreate(node);
			it.remove();
			it.add(brNode);
		}

	}
	
	private void visitNodes(List<AbstractNode> nodes) {
		ListIterator<AbstractNode> it = nodes.listIterator();
		while (it.hasNext()) {
			it.next().accept(this, it);
		}
	}
	
	private void visitProc(Procedure proc) {
		List<AbstractNode> nodes = proc.getNodes();
		BrCounter = 0;
		
		visitNodes(nodes);
		if (proc.getReturnType() instanceof VoidType)
		{
			TypeExpr expr = new TypeExpr(null, new VoidType());
			nodes.add(new ReturnNode(0, null, expr));
		}
	}
	
	private BrNode SelectNodeCreate(IfNode node) {
		
		// Get IfNode attributes
		int id = node.getId();
		Location location = node.getLocation();
		AbstractExpr condition = node.getCondition();
		List<AbstractNode> thenNodes = node.getThenNodes();
		List<AbstractNode> elseNodes = node.getElseNodes();
		JoinNode joinNode = node.getJoinNode();
		
		//Create LabelNode
		LabelNode thenLabelNode = new LabelNode(node.getId(),node.getLocation(), "bb"+ Integer.toString(BrCounter++));
		
		//If thenNode is empty switch with elseNode 
		if (thenNodes.isEmpty())
		{
			thenNodes = elseNodes;
			elseNodes.clear();
		}
		
		//Continue transformation on thenNode
		visitNodes(thenNodes);
			
		//Create label for elseNode
		LabelNode elseLabelNode = new LabelNode(node.getId(),node.getLocation(), "bb"+ Integer.toString(BrCounter++));
		LabelNode endLabelNode = null;
		
		if (!elseNodes.isEmpty()){
			visitNodes(elseNodes);
			endLabelNode = new LabelNode(node.getId(),node.getLocation(), "bb"+ Integer.toString(BrCounter++));	
		}
		
		return new BrNode(id, location, condition, thenNodes, elseNodes, joinNode, thenLabelNode, elseLabelNode, endLabelNode);
	}
	
	private BrNode BrNodeCreate(IfNode node) {
		
		// Get IfNode attributes
		int id = node.getId();
		Location location = node.getLocation();
		AbstractExpr condition = node.getCondition();
		List<AbstractNode> thenNodes = node.getThenNodes();
		List<AbstractNode> elseNodes = node.getElseNodes();
		JoinNode joinNode = node.getJoinNode();
		
		//Create LabelNode
		LabelNode thenLabelNode = new LabelNode(node.getId(),node.getLocation(), "bb"+ Integer.toString(BrCounter++));
		
		//If thenNode is empty switch with elseNode 
		if (thenNodes.isEmpty())
		{
			thenNodes = elseNodes;
			elseNodes.clear();
		}
		
		//Continue transformation on thenNode
		visitNodes(thenNodes);
			
		//Create label for elseNode
		LabelNode elseLabelNode = new LabelNode(node.getId(),node.getLocation(), "bb"+ Integer.toString(BrCounter++));
		LabelNode endLabelNode = null;
		
		if (!elseNodes.isEmpty()){
			visitNodes(elseNodes);
			endLabelNode = new LabelNode(node.getId(),node.getLocation(), "bb"+ Integer.toString(BrCounter++));	
		}
		
		return new BrNode(id, location, condition, thenNodes, elseNodes, joinNode, thenLabelNode, elseLabelNode, endLabelNode);
	}
}
