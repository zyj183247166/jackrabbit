/*
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.jcr.core.nodetype;

import org.apache.jackrabbit.jcr.core.QName;

import java.util.Arrays;

/**
 * A <code>ChildNodeDef</code> ...
 *
 * @author Stefan Guggisberg
 * @version $Revision: 1.25 $, $Date: 2004/09/09 15:23:43 $
 */
public class ChildNodeDef extends ChildItemDef {

    private QName defaultPrimaryType = null;
    private QName[] requiredPrimaryTypes = new QName[]{NodeTypeRegistry.NT_BASE};
    private boolean allowSameNameSibs = false;

    /**
     * Default constructor.
     */
    public ChildNodeDef() {
    }

    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	}
	if (obj instanceof ChildNodeDef) {
	    ChildNodeDef other = (ChildNodeDef) obj;
	    return super.equals(obj)
		    && Arrays.equals(requiredPrimaryTypes, requiredPrimaryTypes)
		    && (defaultPrimaryType == null ? other.defaultPrimaryType == null : defaultPrimaryType.equals(other.defaultPrimaryType))
		    && allowSameNameSibs == other.allowSameNameSibs;
	}
	return false;
    }

    /**
     * @param defaultNodeType
     */
    public void setDefaultPrimaryType(QName defaultNodeType) {
	this.defaultPrimaryType = defaultNodeType;
    }

    /**
     * @param requiredPrimaryTypes
     */
    public void setRequiredPrimaryTypes(QName[] requiredPrimaryTypes) {
	this.requiredPrimaryTypes = requiredPrimaryTypes;
    }

    /**
     * @param allowSameNameSibs
     */
    public void setAllowSameNameSibs(boolean allowSameNameSibs) {
	this.allowSameNameSibs = allowSameNameSibs;
    }

    /**
     * @return
     */
    public QName getDefaultPrimaryType() {
	return defaultPrimaryType;
    }

    /**
     * @return
     */
    public QName[] getRequiredPrimaryTypes() {
	return requiredPrimaryTypes;
    }

    /**
     * @return
     */
    public boolean allowSameNameSibs() {
	return allowSameNameSibs;
    }

    /**
     * @return
     */
    public boolean definesNode() {
	return true;
    }
}
