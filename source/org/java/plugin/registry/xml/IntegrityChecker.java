/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2004-2005 Dmitry Olshansky
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

import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.java.plugin.PathResolver;
import org.java.plugin.registry.Identity;
import org.java.plugin.registry.IntegrityCheckReport;
import org.java.plugin.util.IoUtil;
import org.java.plugin.util.ResourceManager;


/**
 * @version $Id: IntegrityChecker.java,v 1.3 2005/12/03 14:38:29 ddimon Exp $
 */
class IntegrityChecker implements IntegrityCheckReport {
    private static Log log = LogFactory.getLog(IntegrityChecker.class);

    private final PluginRegistryImpl registry;
    private List items = new LinkedList();
    private int errorsCount;
    private int warningsCount;

    IntegrityChecker(final PluginRegistryImpl aRegistry,
            final Collection anItems) {
        this.items = new LinkedList();
        this.registry = aRegistry;
        for (Iterator it = anItems.iterator(); it.hasNext();) {
            ReportItem item = (ReportItem) it.next();
            if (item.getSeverity() == ReportItem.SEVERITY_ERROR) {
                errorsCount++;
            } else if (item.getSeverity() == ReportItem.SEVERITY_WARNING) {
                warningsCount++;
            }
            this.items.add(item);
        }
    }
    
    void doCheck(final PathResolver pathResolver) {
        int count = 0;
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, null,
                ReportItem.ERROR_NO_ERROR, "pluginsCheckStart", null)); //$NON-NLS-1$
        try {
            for (Iterator it = registry.getPluginDescriptors().iterator();
                    it.hasNext();) {
                PluginDescriptorImpl descr = (PluginDescriptorImpl) it.next();
                count++;
                items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                        ReportItem.ERROR_NO_ERROR, "pluginCheckStart", //$NON-NLS-1$
                        descr.getUniqueId()));
                checkPlugin(descr, pathResolver);
                items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                        ReportItem.ERROR_NO_ERROR, "pluginCheckFinish", //$NON-NLS-1$
                        descr.getUniqueId()));
            }
        } catch (Exception e) {
            log.error("integrity check failed for registry " + registry, e); //$NON-NLS-1$
            errorsCount++;
            items.add(new ReportItemImpl(ReportItem.SEVERITY_ERROR, null,
                    ReportItem.ERROR_CHECKER_FAULT, "pluginsCheckError", e)); //$NON-NLS-1$
        }
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, null,
                ReportItem.ERROR_NO_ERROR, "pluginsCheckFinish", //$NON-NLS-1$
                new Integer(count)));
    }
    
    private void checkPlugin(final PluginDescriptorImpl descr,
            final PathResolver pathResolver) {
        // checking prerequisites
        int count = 0;
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                ReportItem.ERROR_NO_ERROR, "prerequisitesCheckStart", //$NON-NLS-1$
                descr.getUniqueId()));
        for (Iterator it = descr.getPrerequisites().iterator(); it.hasNext();) {
            PluginPrerequisiteImpl pre = (PluginPrerequisiteImpl) it.next();
            count++;
            if (!pre.isOptional() && !pre.matches()) {
                errorsCount++;
                items.add(new ReportItemImpl(ReportItem.SEVERITY_ERROR, descr,
                        ReportItem.ERROR_UNSATISFIED_PREREQUISITE,
                        "unsatisfiedPrerequisite", new Object[] { //$NON-NLS-1$
                        pre.getPluginId(), descr.getUniqueId()}));
            }
        }
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                ReportItem.ERROR_NO_ERROR, "prerequisitesCheckFinish", //$NON-NLS-1$
                new Object[] {new Integer(count), descr.getUniqueId()}));
        // checking libraries
        if (pathResolver != null) {
            count = 0;
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                    ReportItem.ERROR_NO_ERROR, "librariesCheckStart", //$NON-NLS-1$
                    descr.getUniqueId()));
            for (Iterator it = descr.getLibraries().iterator(); it.hasNext();) {
                LibraryImpl lib = (LibraryImpl) it.next();
                count++;
                URL url = pathResolver.resolvePath(lib, lib.getPath());
                if (!IoUtil.isResourceExists(url)) {
                    errorsCount++;
                    items.add(new ReportItemImpl(ReportItem.SEVERITY_ERROR, lib,
                            ReportItem.ERROR_BAD_LIBRARY,
                            "accesToResourceFailed", new Object[] { //$NON-NLS-1$
                            lib.getUniqueId(), descr.getUniqueId(), url}));
                }
            }
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                    ReportItem.ERROR_NO_ERROR, "librariesCheckFinish", //$NON-NLS-1$
                    new Object[] {new Integer(count), descr.getUniqueId()}));
        } else {
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                    ReportItem.ERROR_NO_ERROR, "librariesCheckSkip", //$NON-NLS-1$
                    descr.getUniqueId()));
        }
        // checking extension points
        count = 0;
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                ReportItem.ERROR_NO_ERROR, "extPointsCheckStart", null)); //$NON-NLS-1$
        for (Iterator it = descr.getExtensionPoints().iterator();
                it.hasNext();) {
            count++;
            ExtensionPointImpl extPoint = (ExtensionPointImpl) it.next();
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, extPoint,
                    ReportItem.ERROR_NO_ERROR, "extPointCheckStart", //$NON-NLS-1$
                    extPoint.getUniqueId()));
            Collection extPointItems = extPoint.validate();
            for (Iterator it2 = extPointItems.iterator(); it2.hasNext();) {
                ReportItem item = (ReportItem) it2.next();
                if (item.getSeverity() == ReportItem.SEVERITY_ERROR) {
                    errorsCount++;
                } else if (item.getSeverity() == ReportItem.SEVERITY_WARNING) {
                    warningsCount++;
                }
                items.add(item);
            }
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, extPoint,
                    ReportItem.ERROR_NO_ERROR, "extPointCheckFinish", //$NON-NLS-1$
                    extPoint.getUniqueId()));
        }
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                ReportItem.ERROR_NO_ERROR, "extPointsCheckFinish", //$NON-NLS-1$
                new Object[] {new Integer(count), descr.getUniqueId()}));
        // checking extensions
        count = 0;
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                ReportItem.ERROR_NO_ERROR, "extsCheckStart", null)); //$NON-NLS-1$
        for (Iterator it = descr.getExtensions().iterator(); it.hasNext();) {
            count++;
            ExtensionImpl ext = (ExtensionImpl) it.next();
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, ext,
                    ReportItem.ERROR_NO_ERROR, "extCheckStart", //$NON-NLS-1$
                    ext.getUniqueId()));
            Collection extItems = ext.validate();
            for (Iterator it2 = extItems.iterator(); it2.hasNext();) {
                ReportItem item = (ReportItem) it2.next();
                if (item.getSeverity() == ReportItem.SEVERITY_ERROR) {
                    errorsCount++;
                } else if (item.getSeverity() == ReportItem.SEVERITY_WARNING) {
                    warningsCount++;
                }
                items.add(item);
            }
            items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, ext,
                    ReportItem.ERROR_NO_ERROR, "extCheckFinish", //$NON-NLS-1$
                    ext.getUniqueId()));
        }
        items.add(new ReportItemImpl(ReportItem.SEVERITY_INFO, descr,
                ReportItem.ERROR_NO_ERROR, "extsCheckFinish", //$NON-NLS-1$
                new Object[] {new Integer(count), descr.getUniqueId()}));
    }
    
    /**
     * @see org.java.plugin.registry.IntegrityCheckReport#countErrors()
     */
    public int countErrors() {
        return errorsCount;
    }

    /**
     * @see org.java.plugin.registry.IntegrityCheckReport#countWarnings()
     */
    public int countWarnings() {
        return warningsCount;
    }

    /**
     * @see org.java.plugin.registry.IntegrityCheckReport#getItems()
     */
    public Collection getItems() {
        return items;
    }

    static class ReportItemImpl implements ReportItem {
        private final byte severity;
        private final Identity source;
        private final int code;
        private final String msg;
        private final Object data;
        
        ReportItemImpl(final byte aSeverity, final Identity aSource,
                final int aCode, final String aMsg, final Object aData) {
            severity = aSeverity;
            source = aSource;
            code = aCode;
            msg = aMsg;
            data = aData;
        }
        
        /**
         * @see org.java.plugin.registry.IntegrityCheckReport.ReportItem#getCode()
         */
        public int getCode() {
            return code;
        }
        
        /**
         * @see org.java.plugin.registry.IntegrityCheckReport.ReportItem#getMessage()
         */
        public String getMessage() {
            return ResourceManager.getMessage(PluginRegistryImpl.PACKAGE_NAME,
                    msg, data);
        }

        /**
         * @see org.java.plugin.registry.IntegrityCheckReport.ReportItem#getMessage(
         *      java.util.Locale)
         */
        public String getMessage(Locale locale) {
            return ResourceManager.getMessage(PluginRegistryImpl.PACKAGE_NAME,
                    msg, locale, data);
        }
        
        /**
         * @see org.java.plugin.registry.IntegrityCheckReport.ReportItem#getSeverity()
         */
        public byte getSeverity() {
            return severity;
        }
        
        /**
         * @see org.java.plugin.registry.IntegrityCheckReport.ReportItem#getSource()
         */
        public Identity getSource() {
            return source;
        }
    }
}
