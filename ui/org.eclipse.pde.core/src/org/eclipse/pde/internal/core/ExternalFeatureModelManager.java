/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core;

import java.io.*;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.pde.core.IModelProviderEvent;
import org.eclipse.pde.core.IModelProviderListener;
import org.eclipse.pde.internal.core.feature.ExternalFeatureModel;
import org.eclipse.pde.internal.core.ifeature.IFeature;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;

public class ExternalFeatureModelManager implements IEclipsePreferences.IPreferenceChangeListener {

	/**
	 * Creates a feature model for the feature based on the given feature XML
	 * file.
	 * 
	 * @param manifest feature XML file in the local file system
	 * @return ExternalFeatureModel or null
	 */
	public static IFeatureModel createModel(File manifest) {
		ExternalFeatureModel model = new ExternalFeatureModel();
		model.setInstallLocation(manifest.getParent());
		InputStream stream = null;
		try {
			stream = new BufferedInputStream(new FileInputStream(manifest));
			model.load(stream, false);
			return model;
		} catch (Exception e) {
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}

	private static IFeatureModel[] createModels(URL[] featurePaths, IProgressMonitor monitor) {
		if (monitor == null)
			monitor = new NullProgressMonitor();
		monitor.beginTask("", featurePaths.length); //$NON-NLS-1$
		Map uniqueFeatures = new HashMap();
		for (int i = 0; i < featurePaths.length; i++) {
			File manifest = new File(featurePaths[i].getFile(), "feature.xml"); //$NON-NLS-1$
			if (!manifest.exists() || !manifest.isFile()) {
				monitor.worked(1);
				continue;
			}
			IFeatureModel model = createModel(manifest);
			if (model != null && model.isLoaded()) {
				IFeature feature = model.getFeature();
				uniqueFeatures.put(feature.getId() + "_" + feature.getVersion(), model); //$NON-NLS-1$
			}
			monitor.worked(1);
		}
		Collection models = uniqueFeatures.values();
		return (IFeatureModel[]) models.toArray(new IFeatureModel[models.size()]);
	}

	private Vector fListeners = new Vector();

	private IFeatureModel[] fModels;

	private String fPlatformHome;

	private PDEPreferencesManager fPref;

	public ExternalFeatureModelManager() {
		fPref = PDECore.getDefault().getPreferencesManager();
	}

	public void addModelProviderListener(IModelProviderListener listener) {
		fListeners.add(listener);
	}

	private boolean equalPaths(String path1, String path2) {
		if (path1 == null) {
			if (path2 == null) {
				return true;
			}
			return false;
		}
		if (path2 == null) {
			return false;
		}
		return new File(path1).equals(new File(path2));

	}

	private void fireModelProviderEvent(IModelProviderEvent e) {
		for (Iterator iter = fListeners.iterator(); iter.hasNext();) {
			IModelProviderListener listener = (IModelProviderListener) iter.next();
			listener.modelsChanged(e);
		}
	}

	/**
	 * @param propertyValue
	 * @return String or null
	 */
	private String getPathString(Object propertyValue) {
		if (propertyValue != null && propertyValue instanceof String) {
			String path = (String) propertyValue;
			if (path.length() > 0) {
				return path;
			}
		}
		return null;
	}

	public static IFeatureModel[] createModels(String platformHome, ArrayList additionalLocations, IProgressMonitor monitor) {
		if (platformHome != null && platformHome.length() > 0) {
			URL[] featureURLs = PluginPathFinder.getFeaturePaths(platformHome);

			if (additionalLocations.size() == 0)
				return createModels(featureURLs, monitor);

			File[] dirs = new File[additionalLocations.size()];
			for (int i = 0; i < dirs.length; i++) {
				String directory = additionalLocations.get(i).toString();
				File dir = new File(directory, "features"); //$NON-NLS-1$
				if (!dir.exists())
					dir = new File(directory);
				dirs[i] = dir;
			}

			URL[] newUrls = PluginPathFinder.scanLocations(dirs);

			URL[] result = new URL[featureURLs.length + newUrls.length];
			System.arraycopy(featureURLs, 0, result, 0, featureURLs.length);
			System.arraycopy(newUrls, 0, result, featureURLs.length, newUrls.length);
			return createModels(result, monitor);
		}
		return new IFeatureModel[0];
	}

	public void loadModels(String platformHome, String additionalLocations) {
		IFeatureModel[] oldModels = fModels != null ? fModels : new IFeatureModel[0];
		fModels = createModels(platformHome, parseAdditionalLocations(additionalLocations), null);
		fPlatformHome = platformHome;
		notifyListeners(oldModels, fModels);
	}

	private ArrayList parseAdditionalLocations(String additionalLocations) {
		ArrayList result = new ArrayList();
		StringTokenizer tokenizer = new StringTokenizer(additionalLocations, ","); //$NON-NLS-1$
		while (tokenizer.hasMoreTokens()) {
			result.add(tokenizer.nextToken().trim());
		}
		return result;
	}

	private void notifyListeners(IFeatureModel[] oldModels, IFeatureModel[] newFeatureModels) {
		if (oldModels.length > 0 || newFeatureModels.length > 0) {
			int type = 0;
			if (oldModels.length > 0)
				type |= IModelProviderEvent.MODELS_REMOVED;
			if (newFeatureModels.length > 0)
				type |= IModelProviderEvent.MODELS_ADDED;
			ModelProviderEvent replacedFeatures = new ModelProviderEvent(this, type, newFeatureModels, oldModels, null);
			fireModelProviderEvent(replacedFeatures);
		}

	}

	private synchronized void platformPathChanged(String newHome) {
		if (!equalPaths(newHome, fPlatformHome)) {
			loadModels(newHome, fPref.getString(ICoreConstants.ADDITIONAL_LOCATIONS));
		}
	}

	public void preferenceChange(PreferenceChangeEvent event) {
		if (!ICoreConstants.PLATFORM_PATH.equals(event.getKey())) {
			return;
		}
		String newHome = getPathString(event.getNewValue());
		platformPathChanged(newHome);
	}

	public void removeModelProviderListener(IModelProviderListener listener) {
		fListeners.remove(listener);
	}

	public synchronized void shutdown() {
		fPref.removePreferenceChangeListener(this);
	}

	public synchronized void startup() {
		fPref.addPreferenceChangeListener(this);
		loadModels(fPref.getString(ICoreConstants.PLATFORM_PATH), fPref.getString(ICoreConstants.ADDITIONAL_LOCATIONS));
	}

	public synchronized void reload() {
		loadModels(fPref.getString(ICoreConstants.PLATFORM_PATH), fPref.getString(ICoreConstants.ADDITIONAL_LOCATIONS));
	}

	public IFeatureModel[] getModels() {
		return fModels;
	}
}