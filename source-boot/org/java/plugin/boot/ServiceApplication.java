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
package org.java.plugin.boot;

/**
 * This is "marker" interface to represent a service style application that
 * may be started and stopped.
 *
 * @version $Id: ServiceApplication.java,v 1.3 2005/12/03 14:38:29 ddimon Exp $
 */
public interface ServiceApplication extends Application {
    /**
     * This method should stop the application. Don't call this method directly,
     * use {@link Boot#stopApplication(Application)} instead.
     * @throws Exception if any error has occurred during application stopping
     */
    void stopApplication() throws Exception;
}
