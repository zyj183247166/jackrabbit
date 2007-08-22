/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.spi;

import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.name.Path;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.util.Calendar;

import javax.jcr.RepositoryException;

/**
 * <code>QValueFactory</code> defines methods to create <code>QValue</code>
 * instances.
 */
public interface QValueFactory {

    /**
     * Create a new <code>QValue</code> using the given String representation
     * of the value and its {@link javax.jcr.PropertyType type}.
     *
     * @param value String representation of the new <code>QValue</code>. Note,
     * that the given String must never be <code>null</code>.
     * @param type A valid {@link javax.jcr.PropertyType type}.
     * @return a new <code>QValue</code>.
     * @see QValue#getType()
     */
    public QValue create(String value, int type);

    /**
     * Create a new <code>QValue</code> with type {@link javax.jcr.PropertyType#DATE}.
     *
     * @param value A non-null <code>Calendar</code> object acting as value
     * of the new <code>QValue</code>.
     * @return a new <code>QValue</code>.
     */
    public QValue create(Calendar value);

    /**
     * Create a new <code>QValue</code> with type {@link javax.jcr.PropertyType#NAME}.
     *
     * @param value A non-null <code>QName</code>.
     * @return a new <code>QValue</code>.
     */
    public QValue create(QName value);

    /**
     * Create a new <code>QValue</code> with type {@link javax.jcr.PropertyType#PATH}.
     *
     * @param value A non-null <code>Path</code>.
     * @return a new <code>QValue</code>.
     */
    public QValue create(Path value);


    /**
     * Create a new <code>QValue</code> with type {@link javax.jcr.PropertyType#BINARY}.
     *
     * @param value
     * @return a new <code>QValue</code>.
     */
    public QValue create(byte[] value);

    /**
     * Creates a QValue that contains the given binary stream.
     * The given stream is consumed and closed by this method. The type of the
     * resulting QValue will be {@link javax.jcr.PropertyType#BINARY}.
     *
     * @param value binary stream
     * @return a new binary <code>QValue</code>.
     * @throws RepositoryException if the value could not be created
     * @throws IOException if the stream can not be consumed
     */
    public QValue create(InputStream value) throws RepositoryException, IOException;

    /**
     * Create a new <code>QValue</code> with type {@link javax.jcr.PropertyType#BINARY}.
     *
     * @param value
     * @return a new binarly <code>QValue</code>.
     * @throws IOException
     */
    public QValue create(File value) throws IOException;
}
