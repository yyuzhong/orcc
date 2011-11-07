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
package net.sf.orcc.backends.c;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.orcc.df.DfFactory;
import net.sf.orcc.df.Instance;
import net.sf.orcc.df.Network;

/**
 * This class allows the string template accessing informations about
 * application's network
 * 
 * @author Damien de Saint Jorre
 * 
 */
public class CNetworkTemplateData {

	private Map<String, Integer> instanceNameToGroupIdMap;

	private Map<Instance, Integer> instanceToGroupIdMap;

	private int numberOfGroups;

	private List<Instance> getInstancesRecursively(Network network) {
		List<Instance> instances = new ArrayList<Instance>();
		for (Instance instance : network.getInstances()) {
			if (instance.isNetwork()) {
				instances
						.addAll(getInstancesRecursively(instance.getNetwork()));
			} else {
				instances.add(instance);
			}
		}
		return instances;
	}

	public void computeHierarchicalTemplateMaps(Network network) {
		instanceToGroupIdMap = new HashMap<Instance, Integer>();
		numberOfGroups = 0;
		Instance instance = DfFactory.eINSTANCE.createInstance("network",
				network);
		recursiveGroupsComputation(instance, 2);
	}

	public Map<String, Integer> getInstanceNameToGroupIdMap() {
		return instanceNameToGroupIdMap;
	}

	public Map<Instance, Integer> getInstanceToGroupIdMap() {
		return instanceToGroupIdMap;
	}

	public int getNumberOfGroups() {
		return numberOfGroups;
	}

	private boolean isLastedHierarchy(Network network) {
		for (Instance instance : network.getInstances()) {
			if (instance.isNetwork()) {
				return false;
			}
		}
		return true;
	}

	private boolean isNthLastedHierarchy(Network network, int n) {
		if (n == 1) {
			return isLastedHierarchy(network);
		}
		for (Instance instance : network.getInstances()) {
			if (instance.isNetwork()) {
				if (!isNthLastedHierarchy(instance.getNetwork(), n - 1)) {
					return false;
				}
			}
		}
		return true;
	}

	private void recursiveGroupsComputation(Instance instance,
			int hierarchyLevel) {
		if (instance.isNetwork()) {
			Network network = instance.getNetwork();
			if (isNthLastedHierarchy(network, hierarchyLevel)) {
				for (Instance subInstance : getInstancesRecursively(network)) {
					instanceToGroupIdMap.put(subInstance, numberOfGroups);
				}
				numberOfGroups++;
			} else {
				for (Instance subInstance : network.getInstances()) {
					recursiveGroupsComputation(subInstance, hierarchyLevel);
				}
			}
		} else {
			instanceToGroupIdMap.put(instance, numberOfGroups);
			numberOfGroups++;
		}
	}

}
