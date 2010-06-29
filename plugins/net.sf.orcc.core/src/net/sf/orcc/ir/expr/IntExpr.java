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
package net.sf.orcc.ir.expr;

import net.sf.orcc.ir.IrFactory;
import net.sf.orcc.ir.Location;
import net.sf.orcc.ir.Type;

/**
 * This class defines an integer expression.
 * 
 * @author Matthieu Wipliez
 * @author J�r�me Gorin
 * 
 */
public class IntExpr extends AbstractExpression {

	private int value;

	/**
	 * Creates a new integer expression with a dummy location.
	 * 
	 * @param value
	 *            an integer value.
	 */
	public IntExpr(int value) {
		this(new Location(), value);
	}

	/**
	 * Creates a new integer expression with a location.
	 * 
	 * @param location
	 *            a location
	 * @param value
	 *            an integer value.
	 */
	public IntExpr(Location location, int value) {
		super(location);
		this.value = value;
	}

	@Override
	public Object accept(ExpressionInterpreter interpreter, Object... args) {
		return interpreter.interpret(this, args);
	}

	@Override
	public void accept(ExpressionVisitor visitor, Object... args) {
		visitor.visit(this, args);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof IntExpr) {
			return (value == ((IntExpr) obj).value);
		} else {
			return false;
		}
	}

	@Override
	public Type getType() {
		// we return a signed type

		int size;
		if (value == 0) {
			size = 1;
		} else if (value > 0) {
			size = (int) (Math.log(value) / Math.log(2.0)) + 2;
		} else {
			size = (int) Math.ceil(Math.log(-value) / Math.log(2.0)) + 1;
		}

		return IrFactory.eINSTANCE.createTypeInt(size);
	}

	public int getValue() {
		return value;
	}

	@Override
	public boolean isIntExpr() {
		return true;
	}

	public void setValue(int value) {
		this.value = value;
	}

}
