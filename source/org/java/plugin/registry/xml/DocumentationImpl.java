/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2004-2006 Dmitry Olshansky
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *****************************************************************************/
package org.java.plugin.registry.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.java.plugin.registry.Documentation;
import org.java.plugin.registry.Identity;

/**
 * @version $Id: DocumentationImpl.java,v 1.2 2006/06/05 18:16:43 ddimon Exp $
 */
class DocumentationImpl implements Documentation {
    /**
     *  Logger object.
     */
    protected static Log log = LogFactory.getLog(DocumentationImpl.class);

    private final Identity identity;
    private final ModelDocumentation model;
    private List references;
    
    DocumentationImpl(final Identity anIdentity,
            final ModelDocumentation aModel) {
        identity = anIdentity;
        model = aModel;
        if ((model.getCaption() == null)
                || (model.getCaption().trim().length() == 0)) {
            model.setCaption(""); //$NON-NLS-1$
        }
        references = new ArrayList(model.getReferences().size());
        for (Iterator it = model.getReferences().iterator(); it.hasNext();) {
            references.add(new ReferenceImpl(
                    (ModelDocumentationReference) it.next()));
        }
        references = Collections.unmodifiableList(references);
        if (model.getText() == null) {
            model.setText(""); //$NON-NLS-1$
        }
        if (log.isDebugEnabled()) {
            log.debug("object instantiated: " + this); //$NON-NLS-1$
        }
    }

    /**
     * @see org.java.plugin.registry.Documentation#getCaption()
     */
    public String getCaption() {
        return model.getCaption();
    }

    /**
     * @see org.java.plugin.registry.Documentation#getText()
     */
    public String getText() {
        return model.getText();
    }

    /**
     * @see org.java.plugin.registry.Documentation#getReferences()
     */
    public Collection getReferences() {
        return references;
    }

    /**
     * @see org.java.plugin.registry.Documentation#getDeclaringIdentity()
     */
    public Identity getDeclaringIdentity() {
        return identity;
    }
    
    private class ReferenceImpl implements Reference {
        private final ModelDocumentationReference modelRef;

        ReferenceImpl(final ModelDocumentationReference aModel) {
            modelRef = aModel;
            if ((modelRef.getCaption() == null)
                    || (modelRef.getCaption().trim().length() == 0)) {
                modelRef.setCaption(""); //$NON-NLS-1$
            }
            if ((modelRef.getPath() == null)
                    || (modelRef.getPath().trim().length() == 0)) {
                modelRef.setPath(""); //$NON-NLS-1$
            }
            if (log.isDebugEnabled()) {
                log.debug("object instantiated: " + this); //$NON-NLS-1$
            }
        }

        /**
         * @see org.java.plugin.registry.Documentation.Reference#getCaption()
         */
        public String getCaption() {
            return modelRef.getCaption();
        }

        /**
         * @see org.java.plugin.registry.Documentation.Reference#getRef()
         */
        public String getRef() {
            return modelRef.getPath();
        }

        /**
         * @see org.java.plugin.registry.Documentation.Reference#getDeclaringIdentity()
         */
        public Identity getDeclaringIdentity() {
            return DocumentationImpl.this.getDeclaringIdentity();
        }
    }
}
