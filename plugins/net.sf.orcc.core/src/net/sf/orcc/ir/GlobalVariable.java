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
package net.sf.orcc.ir;

/**
 * This class represents a global variable. A global variable is a variable that
 * is not assignable and may have a value as an expression.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class GlobalVariable extends Variable {

	/**
	 * initial value, if present, <code>null</code> otherwise
	 */
	private Expression initialValue;

	/**
	 * Creates a new global variable with the given location, type, and name.
	 * The global variable created will have no value.
	 * 
	 * @param location
	 *            the global variable location
	 * @param type
	 *            the global variable type
	 * @param name
	 *            the global variable name
	 */
	public GlobalVariable(Location location, Type type, String name) {
		this(location, type, name, null);
	}

	/**
	 * Creates a new global variable with the given location, type, name, and
	 * initial value.
	 * 
	 * @param location
	 *            the global variable location
	 * @param type
	 *            the global variable type
	 * @param name
	 *            the global variable name
	 * @param value
	 *            the value of the global variable
	 */
	public GlobalVariable(Location location, Type type, String name,
			Expression value) {
		super(location, type, name, true);
		this.initialValue = value;
	}
	
	/**
	 * Creates a new state variable with the given location, type, name and
	 * initial value expressed as an expression.
	 * 
	 * @param location
	 *            the state variable location
	 * @param type
	 *            the state variable type
	 * @param name
	 *            the state variable name
	 * @param value
	 *            initial value
	 */
	public GlobalVariable(Location location, Type type, String name,
			boolean assignable) {
		this(location, type, name, assignable, null);
	}

	/**
	 * Creates a new state variable with the given location, type, name and
	 * initial value.
	 * 
	 * @param location
	 *            the state variable location
	 * @param type
	 *            the state variable type
	 * @param name
	 *            the state variable name
	 * @param value
	 *            initial value
	 */
	public GlobalVariable(Location location, Type type, String name,
			boolean assignable, Expression initialValue) {
		super(location, type, name, true, assignable);
		this.initialValue = initialValue;
	}

	/**
	 * Returns the initial expression of this variable.
	 * 
	 * @return the initial expression of this variable
	 */
	public Expression getExpression() {
		return initialValue;
	}

	/**
	 * Returns the initial value of this state variable, or <code>null</code> if
	 * this variable has no initial constant value. The initial value may be a
	 * boolean, an integer, a list or a string.
	 * 
	 * @return an object, or <code>null</code> if this variable has no constant
	 *         value
	 */
	public Expression getConstantValue() {
		return initialValue;
	}

	/**
	 * Returns <code>true</code> if this state variable has an initial value.
	 * 
	 * @return <code>true</code> if this state variable has an initial value
	 */
	public boolean isInitialized() {
		return (initialValue != null);
	}

	/**
	 * Returns <code>true</code> if this variable has an initial expression.
	 * 
	 * @return <code>true</code> if this variable has an initial expression
	 */
	public boolean hasExpression() {
		return (initialValue != null);
	}

	/**
	 * Sets the initial value of this state variable. The initial value may be a
	 * boolean, an integer, a list or a string.
	 * 
	 * @param initialValue
	 *            an object, or <code>null</code> if this variable has no
	 *            constant value
	 */
	public void setConstantValue(Expression initialValue) {
		this.initialValue = initialValue;
	}

	/**
	 * Sets the initial expression of this variable.
	 * 
	 * @param expression
	 *            the initial expression of this variable
	 */
	public void setExpression(Expression expression) {
		this.initialValue = expression;
	}

}
