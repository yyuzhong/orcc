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
package net.sf.orcc.ir.instructions;

import java.util.List;

import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.Location;
import net.sf.orcc.ir.Type;
import net.sf.orcc.ir.Use;
import net.sf.orcc.ir.ValueContainer;
import net.sf.orcc.ir.util.CommonNodeOperations;

/**
 * This class defines a instruction that Stores data to memory from an
 * expression. The target can be a global (scalar or array), or a local array.
 * 
 * @author Matthieu Wipliez
 * 
 */
public class Store extends AbstractInstruction implements ValueContainer {

	private List<Expression> indexes;

	private Use target;

	private Expression value;

	public Store(Location location, Use target, List<Expression> indexes,
			Expression value) {
		super(location);
		setIndexes(indexes);
		setTarget(target);
		setValue(value);
	}

	public Store(Use target, List<Expression> indexes, Expression value) {
		this(new Location(), target, indexes, value);
	}

	@Override
	public Object accept(InstructionInterpreter interpreter, Object... args) {
		return interpreter.interpret(this, args);
	}

	@Override
	public void accept(InstructionVisitor visitor, Object... args) {
		visitor.visit(this, args);
	}

	@Override
	public Type getCast(){
		Type expr = value.getType();
		Type val = target.getVariable().getType();
		if(expr != null){
			if (!expr.equals(val)){
				if (!expr.toString().equals(val.toString())){
					return val;
				}
			}
		}
		
		return null;
	}

	public List<Expression> getIndexes() {
		return indexes;
	}

	/**
	 * Returns the target of this Store. The target is a {@link Use}.
	 * 
	 * @return the target of this Store
	 */
	public Use getTarget() {
		return target;
	}

	@Override
	public Expression getValue() {
		return value;
	}

	/**
	 * Sets the indexes of this store node. Uses are updated to point to this
	 * node.
	 * 
	 * @param indexes
	 *            a list of expressions
	 */
	private void setIndexes(List<Expression> indexes) {
		if (this.indexes != null) {
			Use.removeUses(this, this.indexes);
		}
		this.indexes = indexes;
		Use.addUses(this, indexes);
	}

	public void setTarget(Use target) {
		if (this.target != null) {
			this.target.remove();
		}
		this.target = target;
		target.setNode(this);
	}

	@Override
	public void setValue(Expression value) {
		CommonNodeOperations.setValue(this, value);
	}

	@Override
	public void setValueSimple(Expression value) {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return target.toString() + indexes + " = " + getValue();
	}

}
