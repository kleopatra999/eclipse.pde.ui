/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.elements;

import org.eclipse.swt.graphics.Image;

public class NamedElement extends DefaultElement {
	protected Image image;

	private String name;

	private IPDEElement parent;

	private Object[] children;

	public NamedElement(String name) {
		this(name, null, null);
	}

	public NamedElement(String name, Image icon) {
		this(name, icon, null);
	}

	public NamedElement(String name, Image image, IPDEElement parent) {
		this.name = name;
		this.image = image;
		this.parent = parent;
	}

	/**
	 * Sets the children of this element. The children are not copied
	 * @param children
	 */
	public void setChildren(Object[] children) {
		this.children = children;
	}

	/**
	 * Gets the children of this element.  The children are not copied
	 */
	public Object[] getChildren() {
		return children;
	}

	public Image getImage() {
		return image;
	}

	public String getLabel() {
		return name;
	}

	public Object getParent() {
		return parent;
	}

	public String toString() {
		return getLabel();
	}
}
