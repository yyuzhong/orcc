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
package net.sf.orcc.backends.c.transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.sf.orcc.ir.CFGNode;
import net.sf.orcc.ir.Instruction;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.instructions.Read;
import net.sf.orcc.ir.instructions.ReadEnd;
import net.sf.orcc.ir.instructions.Write;
import net.sf.orcc.ir.instructions.WriteEnd;
import net.sf.orcc.ir.nodes.BlockNode;
import net.sf.orcc.ir.nodes.IfNode;
import net.sf.orcc.ir.transforms.AbstractActorTransformation;

/**
 * Move writes to the beginning of an action (because we use pointers).
 * 
 * @author Matthieu Wipliez
 * 
 */
public class MoveReadsWritesTransformation extends AbstractActorTransformation {

	private List<Instruction> readEnds;

	private List<Instruction> writes;

	public MoveReadsWritesTransformation() {
		writes = new ArrayList<Instruction>();
		readEnds = new ArrayList<Instruction>();
	}

	@Override
	public void visit(Read read, Object... args) {
		readEnds.add(new ReadEnd(read));
	}

	@Override
	@SuppressWarnings("unchecked")
	public void visit(Write write, Object... args) {
		ListIterator<Instruction> it = (ListIterator<Instruction>) args[0];
		writes.add(write);
		it.set(new WriteEnd(write));
	}

	@Override
	public void visitProcedure(Procedure procedure) {
		super.visitProcedure(procedure);

		List<CFGNode> nodes = procedure.getNodes();

		// add writes at the beginning of the node list, and read at the ends
		BlockNode.getFirst(procedure, nodes).getInstructions()
				.addAll(0, writes);

		// Put readend nodes before last instruction in the last block.
		// In the general case the last block is expected to contain a return
		// instruction in functions/procedures and actions.
		// HOWEVER (this is something that we should change in the front-end),
		// the last block is expected to be empty in isSchedulable functions,
		// thus the "numInstructions > 0" test.
		BlockNode block;
		CFGNode last = nodes.get(nodes.size() - 1);
		if (last instanceof BlockNode) {
			block = (BlockNode) last;
		} else if (last instanceof IfNode) {
			block = ((IfNode) last).getJoinNode();
		} else {
			block = new BlockNode(procedure);
			nodes.add(block);
		}

		List<Instruction> instructions = block.getInstructions();
		int numInstructions = instructions.size();
		instructions.addAll(numInstructions > 0 ? numInstructions - 1 : 0,
				readEnds);

		// clears the lists
		writes.clear();
		readEnds.clear();
	}
}
