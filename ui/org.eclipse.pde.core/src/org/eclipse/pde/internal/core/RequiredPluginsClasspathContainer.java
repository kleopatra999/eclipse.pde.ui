/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core;

import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.osgi.service.resolver.BaseDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.HostSpecification;
import org.eclipse.pde.core.plugin.IPluginModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;

public class RequiredPluginsClasspathContainer implements IClasspathContainer {
	private IPluginModelBase fModel;
	private ArrayList fEntries;
	
	private static boolean DEBUG = false;
	
	static {
		DEBUG  = PDECore.getDefault().isDebugging() 
					&& "true".equals(Platform.getDebugOption("org.eclipse.pde.core/classpath")); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Constructor for RequiredPluginsClasspathContainer.
	 */
	public RequiredPluginsClasspathContainer(IPluginModelBase model) {
		fModel = model;
	}

	
	public void reset() {
		fEntries = null;
	}

	/**
	 * @see org.eclipse.jdt.core.IClasspathContainer#getKind()
	 */
	public int getKind() {
		return K_APPLICATION;
	}

	/**
	 * @see org.eclipse.jdt.core.IClasspathContainer#getPath()
	 */
	public IPath getPath() {
		return new Path(PDECore.CLASSPATH_CONTAINER_ID);
	}
	
	/**
	 * @see org.eclipse.jdt.core.IClasspathContainer#getDescription()
	 */
	public String getDescription() {
		return PDECoreMessages.RequiredPluginsClasspathContainer_description; //$NON-NLS-1$
	}
	
	/**
	 * @see org.eclipse.jdt.core.IClasspathContainer#getClasspathEntries()
	 */
	public IClasspathEntry[] getClasspathEntries() {
		if (fModel == null) {
			if (DEBUG) {
				System.out.println("********Returned an empty container"); //$NON-NLS-1$
				System.out.println();
			}
			return new IClasspathEntry[0];
		}
		if (fEntries == null) {
			computePluginEntries();
		}
		if (DEBUG) {
			System.out.println("Dependencies for plugin '" + fModel.getPluginBase().getId() + "':"); //$NON-NLS-1$ //$NON-NLS-2$
			for (int i = 0; i < fEntries.size(); i++) {
				System.out.println(fEntries.get(i).toString());
			}
			System.out.println();
		}
		return (IClasspathEntry[])fEntries.toArray(new IClasspathEntry[fEntries.size()]);
	}

	private void computePluginEntries() {
		fEntries = new ArrayList();
		try {			
			BundleDescription desc = fModel.getBundleDescription();
			if (desc == null)
				return;

			Platform.getPlatformAdmin().getStateHelper().getVisiblePackages(desc);
			HashSet added = new HashSet();

			HostSpecification host = desc.getHost();
			if (desc.isResolved() && host != null) {
				addHostPlugin(host, added);
			}

			// add dependencies
			BundleSpecification[] required = desc.getRequiredBundles();
			for (int i = 0; i < required.length; i++) {
				if (required[i].isResolved()) {
					addDependency((BundleDescription)required[i].getSupplier(),
							required[i].isExported(), 
							added);
				}
			}

			ClasspathUtilCore.addExtraClasspathEntries(fModel, fEntries);

			// add implicit dependencies
			addImplicitDependencies(added);
		} catch (CoreException e) {
		}
	}


	private void addDependency(BundleDescription desc, boolean isExported, HashSet added) throws CoreException {
		if (desc == null || !added.add(desc.getSymbolicName()))
			return;

		boolean inWorkspace = addPlugin(desc, isExported, true, added);

		if (hasExtensibleAPI(desc)) {
			BundleDescription[] fragments = desc.getFragments();
			for (int i = 0; i < fragments.length; i++) {
				if (fragments[i].isResolved())
					addDependency(fragments[i], isExported, added);
			}
		}

		if (!inWorkspace) {
			BundleSpecification[] required = desc.getRequiredBundles();
			for (int i = 0; i < required.length; i++) {
				if (required[i].isResolved() && required[i].isExported()) {
					BundleDescription supplier = (BundleDescription)required[i].getSupplier();
					addDependency(supplier, isExported, added);
				}
			}
		}
	}

	private boolean addPlugin(BundleDescription desc, boolean isExported,
			boolean useInclusionPatterns, HashSet added)
			throws CoreException {		
		IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(desc);
		IResource resource = model.getUnderlyingResource();
		if (resource != null) {
			ClasspathUtilCore.addProjectEntry(model, isExported, useInclusionPatterns, fEntries);
		} else {
			ClasspathUtilCore.addLibraries(model, isExported, useInclusionPatterns, fEntries);
		}
		return resource != null;
	}

	private void addImplicitDependencies(HashSet added) throws CoreException {

		String id = fModel.getPluginBase().getId();
		String schemaVersion = fModel.getPluginBase().getSchemaVersion();
		boolean isOSGi = TargetPlatform.isOSGi();
		
		PluginModelManager manager = PDECore.getDefault().getModelManager();

		if ((isOSGi && schemaVersion != null)
				|| id.equals("org.eclipse.core.boot") //$NON-NLS-1$
				|| id.equals("org.apache.xerces") //$NON-NLS-1$
				|| id.startsWith("org.eclipse.swt")) //$NON-NLS-1$
			return;

		if (schemaVersion == null && isOSGi) {
			if (!id.equals("org.eclipse.core.runtime")) { //$NON-NLS-1$
				IPluginModelBase plugin = manager.findModel(
						"org.eclipse.core.runtime.compatibility"); //$NON-NLS-1$
				if (plugin != null)
					addDependency(plugin.getBundleDescription(), false, added);
			}
		} else {
			IPluginModelBase plugin = manager.findModel("org.eclipse.core.boot"); //$NON-NLS-1$
			if (plugin != null)
				addDependency(plugin.getBundleDescription(), false, added);
			
			if (!id.equals("org.eclipse.core.runtime")) { //$NON-NLS-1$
				plugin = manager.findModel("org.eclipse.core.runtime"); //$NON-NLS-1$
				if (plugin != null)
					addDependency(plugin.getBundleDescription(), false, added);
			}
		}
	}

	private void addHostPlugin(HostSpecification hostSpec, HashSet added) throws CoreException {
		BaseDescription desc = hostSpec.getSupplier();
		
		if (desc instanceof BundleDescription && added.add(desc.getName())) {
			BundleDescription host = (BundleDescription)desc;
			// add host plug-in
			boolean inWorkspace = addPlugin(host, false, false, added);
			
			BundleSpecification[] required = host.getRequiredBundles();
			for (int i = 0; i < required.length; i++) {
				// if the plug-in is a project in the workspace, only add
				// non-reexported dependencies since the fragment will
				// automatically get the reexported dependencies.
				// if the plug-in is in the target, then you need to explicit
				// all the parent plug-in's dependencies.
				if ((!inWorkspace || !required[i].isExported()) && required[i].isResolved()) {
					desc = required[i].getSupplier();
					if (desc instanceof BundleDescription) {
						addDependency((BundleDescription)desc, false, added);
					}
				}
			}
		}
	}
	
	private boolean hasExtensibleAPI(BundleDescription desc) {
		IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(desc);
		return (model instanceof IPluginModel) 
					? ClasspathUtilCore.hasExtensibleAPI(((IPluginModel)model).getPlugin()) 
					: false;
	}
	

}
