/*
 * Copyright (c) 2009-2010, LEAD TECH DESIGN Rennes - France
 * Copyright (c) 2009-2010, IETR/INSA of Rennes
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
package net.sf.orcc.backends.vhdl;

import static net.sf.orcc.OrccLaunchConstants.DEBUG_MODE;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.sf.orcc.OrccException;
import net.sf.orcc.backends.AbstractBackend;
import net.sf.orcc.backends.STPrinter;
import net.sf.orcc.backends.transformations.InlineTransformation;
import net.sf.orcc.backends.transformations.ListFlattenTransformation;
import net.sf.orcc.backends.transformations.Multi2MonoToken;
import net.sf.orcc.backends.transformations.VariableRenamer;
import net.sf.orcc.backends.vhdl.transformations.BoolExprTransformation;
import net.sf.orcc.backends.vhdl.transformations.TransformConditionals;
import net.sf.orcc.backends.vhdl.transformations.VariableRedimension;
import net.sf.orcc.interpreter.ActorInterpreter;
import net.sf.orcc.ir.Actor;
import net.sf.orcc.ir.ActorVisitor;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.Procedure;
import net.sf.orcc.ir.transformations.DeadCodeElimination;
import net.sf.orcc.ir.transformations.DeadGlobalElimination;
import net.sf.orcc.ir.transformations.DeadVariableRemoval;
import net.sf.orcc.ir.transformations.PhiRemoval;
import net.sf.orcc.ir.transformations.RenameTransformation;
import net.sf.orcc.network.Instance;
import net.sf.orcc.network.Network;
import net.sf.orcc.network.transformations.BroadcastAdder;

/**
 * VHDL back-end.
 * 
 * @author Nicolas Siret
 * 
 */
public class VHDLBackendImpl extends AbstractBackend {

	public static Pattern adjacentUnderscores = Pattern.compile("_+");

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		main(VHDLBackendImpl.class, args);
	}

	private STPrinter printer;

	private final Map<String, String> transformations;

	public VHDLBackendImpl() {
		transformations = new HashMap<String, String>();
		transformations.put("abs", "abs_1");
		transformations.put("access", "access_1");
		transformations.put("component", "component_1");
		transformations.put("select", "select_1");
	}

	@Override
	protected void doTransformActor(Actor actor) throws OrccException {
		evaluateInitializeActions(actor);

		ActorVisitor[] transformationsCodegen = {
				new InlineTransformation(true, false),

				// cleanup code
				new DeadGlobalElimination(),
				new DeadCodeElimination(),
				new DeadVariableRemoval(false),

				// out-of-SSA transformation
				// must be done before WTF and MAAT passes because having phis
				// span multiple procedures does not make sense
				new PhiRemoval(),

				// TODO: While To FSM transformation
				// must be done before MAAT because MAAT does not handle
				// multiple array accesses in loops
				
				// transforms actions from multi-token to mono-token 
				new Multi2MonoToken(),

				// transform multiple array accesses
				// new MultipleArrayAccessTransformation(),

				// transform "b := a > b;" statements to if conditionals
				new BoolExprTransformation(),

				// transforms "if (b)" to "if (b = true)"
				new TransformConditionals(),

				// flattens multi-dimensional arrays
				//new ListFlattenTransformation(true, false, true),

				// replaces local array of size 1 by scalars
				new VariableRedimension(),

				// renames variables so we can inline them in the template
				// should remain after other transformations
				new VariableRenamer(),

				// renames reserved keywords and replaces adjacent underscores
				// by a single underscore
				new RenameTransformation(this.transformations),
				new RenameTransformation(adjacentUnderscores, "_") };

		// applies transformations
		for (ActorVisitor transformation : transformationsCodegen) {
			transformation.visit(actor);
		}

		VHDLTemplateData templateData = new VHDLTemplateData();
		templateData.visit(actor);
		actor.setTemplateData(templateData.getVariablesList());

		// remove initialization procedure (we could do better)
		Procedure initProc = actor.getProcs().get("_initialize");
		if (initProc != null) {
			initProc.setNative(true);
		}
	}

	@Override
	protected void doVtlCodeGeneration(List<File> files) throws OrccException {
		// do not generate a VHDL VTL
	}

	@Override
	protected void doXdfCodeGeneration(Network network) throws OrccException {
		printer = new STPrinter(getAttribute(DEBUG_MODE, true));
		printer.loadGroup("VHDL_actor");
		printer.setExpressionPrinter(VHDLExpressionPrinter.class);
		printer.setTypePrinter(VHDLTypePrinter.class);

		// checks output folder exists, and if not creates it
		File folder = new File(path + File.separator + "Design");
		if (!folder.exists()) {
			folder.mkdir();
		}

		List<Actor> actors = network.getActors();
		transformActors(actors);
		printActors(actors);

		// print network and subnetworks
		write("Printing network and subnetworks...\n");
		printNetwork(network);
	}

	/**
	 * Evaluates the initialize actions of the given actor.
	 * 
	 * @param actor
	 *            an actor
	 */
	private void evaluateInitializeActions(Actor actor) {
		// initializes the actor
		Map<String, Expression> parameters = Collections.emptyMap();
		ActorInterpreter interpreter = new ActorInterpreter(parameters, actor,
				null);
		try {
			interpreter.initialize();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected boolean printActor(Actor actor) throws OrccException {
		String id = actor.getName();
		String outputName = path + File.separator + "Design" + File.separator
				+ id + ".vhd";
		try {
			return printer.printActor(outputName, actor);
		} catch (IOException e) {
			throw new OrccException("I/O error", e);
		}
	}

	/**
	 * Prints the given network.
	 * 
	 * @param network
	 *            a network
	 * @throws OrccException
	 *             if something goes wrong
	 */
	private void printNetwork(Network network) throws OrccException {
		printer.loadGroup("VHDL_testbench");

		File folder = new File(path + File.separator + "Testbench");
		if (!folder.exists()) {
			folder.mkdir();
		}

		Instance instance = new Instance(network.getName(), network.getName());
		instance.setContents(network);
		printTestbench(instance);

		try {
			printer.loadGroup("VHDL_network");

			// Add broadcasts before printing
			new BroadcastAdder().transform(network);

			String outputName = path + File.separator + "Design"
					+ File.separator + network.getName() + ".vhd";
			printer.printNetwork(outputName, network, false, fifoSize);

			for (Network subNetwork : network.getNetworks()) {
				new BroadcastAdder().transform(subNetwork);
				outputName = path + File.separator + "Design" + File.separator
						+ subNetwork.getName() + ".vhd";
				printer.printNetwork(outputName, subNetwork, false, fifoSize);
			}

			new TCLPrinter().printTCL(path, instance);
		} catch (IOException e) {
			throw new OrccException("I/O error", e);
		}
	}

	private void printTestbench(Instance instance) throws OrccException {
		try {
			String id = instance.getId();
			String outputName = path + File.separator + "Testbench"
					+ File.separator + id + "_tb.vhd";
			printer.printInstance(outputName, instance);

			if (instance.isNetwork()) {
				Network network = instance.getNetwork();
				for (Instance subInstance : network.getInstances()) {
					printTestbench(subInstance);
				}
			}
		} catch (IOException e) {
			throw new OrccException("I/O error", e);
		}
	}

}
