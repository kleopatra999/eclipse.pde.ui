/*
 * Created on Mar 2, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.eclipse.pde.internal.ui.editor;

import org.eclipse.pde.core.IModelChangedListener;

/**
 * @author dejan
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public interface IContextPart extends IModelChangedListener {
	boolean isEditable();
	PDEFormPage getPage();
	String getContextId();
	void fireSaveNeeded();
}
