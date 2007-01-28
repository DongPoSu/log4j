/*
 * Copyright 1999,2004 The Apache Software Foundation.
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

package org.apache.log4j.rule;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.apache.log4j.Level;
import org.apache.log4j.helpers.UtilLoggingLevel;
import org.apache.log4j.spi.LoggingEvent;

/**
 * A Rule class implementing equals against two levels.
 * 
 * @author Scott Deboy <sdeboy@apache.org>
 */
public class LevelEqualsRule extends AbstractRule {
    static final long serialVersionUID = -3638386582899583994L;

    private transient Level level;

    private LevelEqualsRule(Level level) {
        this.level = level;
    }

    public static Rule getRule(String value) {
        Level thisLevel = Level.toLevel(value, null);
        if (thisLevel == null) {
           thisLevel = UtilLoggingLevel.toLevel(value);
        }
        return new LevelEqualsRule(thisLevel);
    }

    public boolean evaluate(LoggingEvent event) {
        return level.equals(event.getLevel());
    }

    /**
     * Deserialize the state of the object
     * 
     * @param in
     * 
     * @throws IOException
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException {
        boolean isUtilLogging = in.readBoolean();
        int levelInt = in.readInt();
        if (isUtilLogging) {
            level = UtilLoggingLevel.toLevel(levelInt);
        } else {
            level = Level.toLevel(levelInt);
        }
    }

    /**
     * Serialize the state of the object
     * 
     * @param out
     * 
     * @throws IOException
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeBoolean(level instanceof UtilLoggingLevel);
        out.writeInt(level.toInt());
    }
}