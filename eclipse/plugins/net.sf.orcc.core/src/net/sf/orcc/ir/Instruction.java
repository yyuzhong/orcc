/*
 * Copyright (c) 2009-2011, IETR/INSA of Rennes
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
package net.sf.orcc.ir;

import org.eclipse.emf.ecore.EObject;

import net.sf.orcc.ir.util.InstructionInterpreter;
import net.sf.orcc.ir.util.InstructionVisitor;

/**
 * This class defines an instruction.
 * 
 * @author Matthieu Wipliez
 * @model abstract="true"
 */
public interface Instruction extends EObject {

	/**
	 * Accepts the given instruction interpreter.
	 * 
	 * @param interpreter
	 *            an interpreter
	 * @param args
	 *            arguments
	 * @return an object
	 */
	Object accept(InstructionInterpreter interpreter, Object... args);

	/**
	 * Accepts the given instruction visitor.
	 * 
	 * @param visitor
	 *            a visitor
	 * @param args
	 *            arguments
	 */
	void accept(InstructionVisitor visitor);

	/**
	 * Returns the block that contains this instruction.
	 * 
	 * @return the block that contains this instruction
	 */
	NodeBlock getBlock();

	/**
	 * Returns a Cast object if this instruction requires casting.
	 * 
	 * @return a Cast object if this instruction requires casting
	 */
	Cast getCast();

	/**
	 * Returns the location of this instruction.
	 * 
	 * @return the location of this instruction
	 * @model containment="true"
	 */
	public Location getLocation();

	/**
	 * Returns the predicate associated with this instruction. This is used by
	 * if-conversion.
	 * 
	 * @return the predicate associated with this instruction
	 * @model
	 */
	Expression getPredicate();

	/**
	 * Sets the value of the '{@link net.sf.orcc.ir.Instruction#getPredicate <em>Predicate</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Predicate</em>' reference.
	 * @see #getPredicate()
	 * @generated
	 */
	void setPredicate(Expression value);

	/**
	 * Returns <code>true</code> if the instruction is an Assign.
	 * 
	 * @return <code>true</code> if the instruction is an Assign
	 */
	boolean isAssign();

	/**
	 * Returns <code>true</code> if the instruction is a Call.
	 * 
	 * @return <code>true</code> if the instruction is a Call
	 */
	boolean isCall();

	/**
	 * Returns <code>true</code> if the instruction is a Load.
	 * 
	 * @return <code>true</code> if the instruction is a Load
	 */
	boolean isLoad();

	/**
	 * Returns <code>true</code> if the instruction is a Phi.
	 * 
	 * @return <code>true</code> if the instruction is a Phi
	 */
	boolean isPhi();

	/**
	 * Returns <code>true</code> if the instruction is a Return.
	 * 
	 * @return <code>true</code> if the instruction is a Return
	 */
	boolean isReturn();

	/**
	 * Returns <code>true</code> if the instruction is a Store.
	 * 
	 * @return <code>true</code> if the instruction is a Store
	 */
	boolean isStore();

	/**
	 * Sets the location of this instruction.
	 * 
	 * @param location
	 *            the location of this instruction
	 */
	void setLocation(Location location);

}
