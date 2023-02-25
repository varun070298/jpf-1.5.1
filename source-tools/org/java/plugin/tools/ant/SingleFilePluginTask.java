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
package org.java.plugin.tools.ant;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.tools.ant.BuildException;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginFragment;
import org.java.plugin.tools.PluginArchiver;

/**
 * The Ant task to create "single file" plug-ins.
 * 
 * @version $Id: SingleFilePluginTask.java,v 1.3 2007/02/10 17:20:24 ddimon Exp $
 */
public class SingleFilePluginTask extends BaseJpfTask {
    private File destDir;

    /**
     * @param aDestDir folder, where to put generated plug-in file(s)
     */
    public void setDestDir(final File aDestDir) {
        destDir = aDestDir;
    }

    /**
     * @see org.apache.tools.ant.Task#execute()
     */
    public void execute() throws BuildException {
        if (destDir == null) {
            throw new BuildException("destdir attribute must be set!", //$NON-NLS-1$
                    getLocation());
        }
        initRegistry(true);
        int count = 0;
        for (Iterator it = getRegistry().getPluginDescriptors().iterator();
                it.hasNext(); count++) {
            PluginDescriptor descr = (PluginDescriptor) it.next();
            File destFile = new File(destDir, descr.getId() + "-" //$NON-NLS-1$
                    + descr.getVersion() + ".zip"); //$NON-NLS-1$
            try {
                PluginArchiver.pack(descr, getPathResolver(), destFile);
            } catch (IOException ioe) {
                throw new BuildException("failed building plug-in file " //$NON-NLS-1$
                        + destFile, ioe, getLocation());
            }
            if (getVerbose()) {
                log("Created plug-in file " + destFile); //$NON-NLS-1$
            }
        }
        for (Iterator it = getRegistry().getPluginFragments().iterator();
                it.hasNext(); count++) {
            PluginFragment fragment = (PluginFragment) it.next();
            File destFile = new File(destDir, fragment.getId() + "-" //$NON-NLS-1$
                    + fragment.getVersion() + ".zip"); //$NON-NLS-1$
            try {
                PluginArchiver.pack(fragment, getPathResolver(), destFile);
            } catch (IOException ioe) {
                throw new BuildException("failed building plug-in fragment file " //$NON-NLS-1$
                        + destFile, ioe, getLocation());
            }
            if (getVerbose()) {
                log("Created plug-in fragment file " + destFile); //$NON-NLS-1$
            }
        }
        log("Plug-in files created " + count); //$NON-NLS-1$
    }
}
