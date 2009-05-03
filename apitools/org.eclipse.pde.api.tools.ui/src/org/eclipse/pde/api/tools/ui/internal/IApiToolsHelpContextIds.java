/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.ui.internal;


/**
 * Listing of ids used a help context ids
 * @since 1.0.0
 */
public interface IApiToolsHelpContextIds {

	public static final String PREFIX = ApiUIPlugin.getPluginIdentifier() + "."; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link ApiProfilesPreferencePage}.
	 */
	public static final String APIBASELINE_PREF_PAGE = PREFIX + "apiprofiles_preference_page"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link ApiProfileWizardPage}
	 */
	public static final String APIPROFILES_WIZARD_PAGE = PREFIX + "apiprofiles_wizard_page"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link ApiToolingSetupWizardPage}
	 */
	public static final String API_TOOLING_SETUP_WIZARD_PAGE = PREFIX + "api_tooling_setup_wizard_page"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link ApiErrorsWarningsPreferencePage}
	 */
	public static final String APITOOLS_ERROR_WARNING_PREF_PAGE = PREFIX + "apitools_error_warning_preference_page"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link ApiToolsErrorWarningsPropertyPage}
	 */
	public static final String APITOOLS_ERROR_WARNING_PROP_PAGE = PREFIX + "apitools_error_warning_prop_page"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link org.eclipse.pde.api.tools.ui.internal.preferences.ProjectSelectionDialog}
	 */
	public static final String APITOOLS_PROJECT_SPECIFIC_SETTINGS_SELECTION_DIALOG = PREFIX + "project_specific_settings_selection_dialog"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link ApiFiltersPropertyPage}
	 */
	public static final String APITOOLS_FILTERS_PROPERTY_PAGE = PREFIX + "apitools_filters_property_page"; //$NON-NLS-1$
	
	/**
	 * Constant representing the help id for the {@link CompareToBaselineWizardPage}
	 */
	public static final String API_COMPARE_WIZARD_PAGE = PREFIX + "api_compare_wizard_page"; //$NON-NLS-1$
		
}