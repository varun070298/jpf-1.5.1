package org.java.plugin.registry.xml;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;

import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.registry.ExtensionPoint.ParameterDefinition;

/**
 * @version $Id: ParameterValueParser.java,v 1.1 2006/08/19 17:37:21 ddimon Exp $
 */
class ParameterValueParser {
    private static ExtensionPoint getExtensionPoint(
            final PluginRegistry registry, final String uniqueId) {
        String pluginId = registry.extractPluginId(uniqueId);
        if (!registry.isPluginDescriptorAvailable(pluginId)) {
            return null;
        }
        String pointId = registry.extractId(uniqueId);
        for (Iterator it = registry.getPluginDescriptor(pluginId)
                .getExtensionPoints().iterator(); it.hasNext();) {
            ExtensionPoint point = (ExtensionPoint) it.next();
            if (point.getId().equals(pointId)) {
                return point;
            }
        }
        return null;
    }

    private Object value;
    private final boolean isParsingSucceeds;
    private String parsingMessage;
    
    ParameterValueParser(final PluginRegistry registry,
            final ParameterDefinition definition, final String rawValue) {
        if (definition == null) {
            parsingMessage = "parameter definition is NULL"; //$NON-NLS-1$
            isParsingSucceeds = false;
            return;
        }
        if (rawValue == null) {
            isParsingSucceeds = true;
            return;
        }
        if (ParameterDefinition.TYPE_ANY.equals(definition.getType())
                || ParameterDefinition.TYPE_NULL.equals(
                        definition.getType())) {
            isParsingSucceeds = true;
            return;
        } else if (ParameterDefinition.TYPE_STRING.equals(
                definition.getType())) {
            value = rawValue;
            isParsingSucceeds = true;
            return;
        }
        String val = rawValue.trim();
        if (val.length() == 0) {
            isParsingSucceeds = true;
            return;
        }
        if (ParameterDefinition.TYPE_BOOLEAN.equals(definition.getType())) {
            if ("true".equals(val)) { //$NON-NLS-1$
                value = Boolean.TRUE;
            } else if ("false".equals(val)) { //$NON-NLS-1$
                value = Boolean.FALSE;
            } else {
                isParsingSucceeds = false;
                return;
            }
        } else if (ParameterDefinition.TYPE_NUMBER.equals(
                definition.getType())) {
            try {
                value =
                    NumberFormat.getInstance(Locale.ENGLISH).parse(val);
            } catch (ParseException nfe) {
                isParsingSucceeds = false;
                return;
            }
        } else if (ParameterDefinition.TYPE_DATE.equals(
                definition.getType())) {
            DateFormat fmt =
                new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH); //$NON-NLS-1$
            try {
                value = fmt.parse(val);
            } catch (ParseException pe) {
                isParsingSucceeds = false;
                return;
            }
        } else if (ParameterDefinition.TYPE_TIME.equals(
                definition.getType())) {
            DateFormat fmt =
                new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH); //$NON-NLS-1$
            try {
                value = fmt.parse(val);
            } catch (ParseException pe) {
                isParsingSucceeds = false;
                return;
            }
        } else if (ParameterDefinition.TYPE_DATETIME.equals(
                definition.getType())) {
            DateFormat fmt =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH); //$NON-NLS-1$
            try {
                value = fmt.parse(val);
            } catch (ParseException pe) {
                isParsingSucceeds = false;
                return;
            }
        } else if (ParameterDefinition.TYPE_PLUGIN_ID.equals(
                definition.getType())) {
            try {
                value = registry.getPluginDescriptor(val);
            } catch (IllegalArgumentException iae) {
                parsingMessage = "unknown plug-in ID " + val; //$NON-NLS-1$
                isParsingSucceeds = false;
                return;
            }
        } else if (ParameterDefinition.TYPE_EXTENSION_POINT_ID.equals(
                definition.getType())) {
            value = getExtensionPoint(registry, val);
            if (value == null) {
                parsingMessage = "unknown extension point UID " + val; //$NON-NLS-1$
                isParsingSucceeds = false;
                return;
            }
            if (definition.getCustomData() != null) {
                ExtensionPoint customExtPoint =
                    getExtensionPoint(registry, definition.getCustomData());
                if (customExtPoint == null) {
                    parsingMessage = "unknown extension point UID " //$NON-NLS-1$
                            + definition.getCustomData()
                            + " provided as custom data"; //$NON-NLS-1$
                    isParsingSucceeds = false;
                    return;
                }
                if (!((ExtensionPoint) value).isSuccessorOf(
                        customExtPoint)) {
                    parsingMessage = "extension point with UID " + val //$NON-NLS-1$
                            + " doesn't \"inherit\" point that is defined" //$NON-NLS-1$
                            + " according to custom data in parameter" //$NON-NLS-1$
                            + " definition - " //$NON-NLS-1$
                            + definition.getCustomData();
                    isParsingSucceeds = false;
                    return;
                }
            }
        } else if (ParameterDefinition.TYPE_EXTENSION_ID.equals(
                definition.getType())) {
            String extId = registry.extractId(val);
            for (Iterator it = registry.getPluginDescriptor(
                    registry.extractPluginId(val)).getExtensions().iterator();
                    it.hasNext();) {
                Extension ext = (Extension) it.next();
                if (ext.getId().equals(extId)) {
                    value = ext;
                    break;
                }
            }
            if (value == null) {
                parsingMessage = "unknown extension UID " + val; //$NON-NLS-1$
                isParsingSucceeds = false;
                return;
            }
            if (definition.getCustomData() != null) {
                ExtensionPoint customExtPoint =
                    getExtensionPoint(registry, definition.getCustomData());
                if (customExtPoint == null) {
                    parsingMessage = "unknown extension point UID " //$NON-NLS-1$
                            + definition.getCustomData()
                            + " provided as custom data in parameter definition " //$NON-NLS-1$
                            + definition;
                    isParsingSucceeds = false;
                    return;
                }
                String extPointUid = registry.makeUniqueId(
                        ((Extension) value).getExtendedPluginId(),
                        ((Extension) value).getExtendedPointId());
                ExtensionPoint extPoint =
                    getExtensionPoint(registry, extPointUid);
                if (extPoint == null) {
                    parsingMessage = "extension point " + extPointUid //$NON-NLS-1$
                            + " is unknown for extension " //$NON-NLS-1$
                            + ((Extension) value).getUniqueId();
                    isParsingSucceeds = false;
                    return;
                }
                if (!extPoint.equals(customExtPoint)
                        && !extPoint.isSuccessorOf(customExtPoint)) {
                    parsingMessage = "extension with UID " + val //$NON-NLS-1$
                            + " extends point that not allowed according" //$NON-NLS-1$
                            + " to custom data defined in parameter" //$NON-NLS-1$
                            + " definition - " //$NON-NLS-1$
                            + definition.getCustomData();
                    isParsingSucceeds = false;
                    return;
                }
            }
        } else if (ParameterDefinition.TYPE_FIXED.equals(
                definition.getType())) {
            for (StringTokenizer st = new StringTokenizer(
                    definition.getCustomData(), "|", false); //$NON-NLS-1$
                    st.hasMoreTokens();) {
                if (val.equals(st.nextToken().trim())) {
                    value = val;
                    isParsingSucceeds = true;
                    return;
                }
            }
            parsingMessage = "not allowed value " + val; //$NON-NLS-1$
            isParsingSucceeds = false;
            return;
        } else if (ParameterDefinition.TYPE_RESOURCE.equals(
                definition.getType())) {
            try {
                value = new URL(val);
            } catch (MalformedURLException mue) {
                parsingMessage = "can't parse value " + val //$NON-NLS-1$
                        + " as an absolute URL, will treat it as relative URL"; //$NON-NLS-1$
                //return Boolean.FALSE;
                value = null;
            }
            isParsingSucceeds = true;
            return;
        }
        isParsingSucceeds = true;
    }
    
    Object getValue() {
        return value;
    }
    
    String getParsingMessage() {
        return parsingMessage;
    }
    
    boolean isParsingSucceeds() {
        return isParsingSucceeds;
    }
}
