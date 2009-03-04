/*
 * Copyright 2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.kernel;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.SequenceInputStream;
import java.util.logging.LogManager;

/**
 * This class is used to initialize Java logging levels at system startup.
 * If the system property {@code java.util.logging.config.class} is set to this
 * class's name, the constructor for this class will be used to initialize
 * logging levels from several sources.
 */
public class LoggerPropertiesInit {

    /**
     * This class is instantiated if the system property 
     * {@code java.util.logging.config.class} is set to this class's
     * name.  Initialize Java logging levels according to the following rules.
     * In each of the rules below, the "source {@code InputStream}" is used
     * to set the logging levels with the 
     * {@link LogManager#readConfiguration(java.io.InputStream)} method:
     * <ul>
     * <li>If the system resource specified by the property
     * {@link BootProperties#DEFAULT_LOG_PROPERTIES} exists, <em>and</em>
     * the file specified by the system property 
     * {@code java.util.logging.config.file} exists, use the concatenation of
     * InputStreams of these two items (in that order) as the source
     * {@code InputStream}.</li>
     * <li>If one and only one of the resources mentioned above exists,
     * use it as the source {@code InputStream}.</li>
     * <li>If neither of the resources mentioned above exist, initialize
     * the logging levels according to the standard rules for the
     * {@link LogManager} as if neither {@code java.util.logging.config.class}
     * nor {@code java.util.logging.config.file} are set.</li>
     * </ul>
     * 
     * @throws java.io.IOException if an error occurs reading the configuration
     *         from the source {@code InputStream}
     */
    public LoggerPropertiesInit() throws IOException {
        init(BootProperties.DEFAULT_LOG_PROPERTIES);
    }
    
    /**
     * This method is used by the no-argument constructor which 
     * passes the value of {@link BootProperties#DEFAULT_LOG_PROPERTIES} as
     * the value of the single {@code defaultLogProperties} parameter.  It
     * provides support for using a custom resource name to facilitate testing.
     * 
     * @param defaultLogProperties the location of the system resource used
     *        to specify logging configuration
     * @throws java.io.IOException
     * @see LoggerPropertiesInit#LoggerPropertiesInit() 
     */
    private static void init(String defaultLogProperties) 
            throws IOException {
        InputStream resourceStream = ClassLoader.getSystemResourceAsStream(
                defaultLogProperties);
        
        InputStream fileStream = null;
        try {
            fileStream = new FileInputStream(
                    new File(System.getProperty(
                             "java.util.logging.config.file")));
        } catch (FileNotFoundException fnfe) {
            //ignore file doesn't exist
        } catch (NullPointerException npe) {
            //ignore null property
        }
        
        if (resourceStream == null && fileStream == null) {
            System.getProperties().remove("java.util.logging.config.class");
            System.getProperties().remove("java.util.logging.config.file");
            LogManager.getLogManager().readConfiguration();
        } else if (resourceStream == null) {
            LogManager.getLogManager().readConfiguration(fileStream);
        } else if (fileStream == null) {
            LogManager.getLogManager().readConfiguration(resourceStream);
        } else {
            SequenceInputStream combinedStream = 
                    new SequenceInputStream(resourceStream, fileStream);
            LogManager.getLogManager().readConfiguration(combinedStream);
        }
    }

}
