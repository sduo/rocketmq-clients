/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.client.message;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.RandomUtils;
import org.apache.rocketmq.client.tools.TestBase;
import org.testng.annotations.Test;

public class MessageTest extends TestBase {

    @Test
    public void testSetTopic() {
        final Message message = new Message(FAKE_TOPIC_0, FAKE_TAG_0, RandomUtils.nextBytes(1));
        final String originalMessageId = message.getMsgId();
        message.setTopic(FAKE_TOPIC_1);
        assertNotEquals(message.getMsgId(), originalMessageId);
    }

    @Test
    public void testSetTag() {
        final Message message = new Message(FAKE_TOPIC_0, FAKE_TAG_0, RandomUtils.nextBytes(1));
        final String originalMessageId = message.getMsgId();
        message.setTag(FAKE_TAG_1);
        assertNotEquals(message.getMsgId(), originalMessageId);
    }

    @Test
    public void testSetKeys() {
        final Message message = new Message(FAKE_TOPIC_0, FAKE_TAG_0, RandomUtils.nextBytes(1));
        List<String> keys = new ArrayList<String>();
        keys.add("keyA");
        message.setKeys(keys);
        assertEquals(message.getKeysList(), keys);
        assertEquals(message.getKeys(), "keyA");
    }

    @Test
    public void testPutUserProperty() {
        final Message message = new Message(FAKE_TOPIC_0, FAKE_TAG_0, RandomUtils.nextBytes(1));
        final String oldMsgId = message.getMsgId();
        message.putUserProperty("key", "value");
        final String newMsgId = message.getMsgId();
        assertNotEquals(oldMsgId, newMsgId);
        assertEquals(message.getUserProperty("key"), "value");
    }

    @Test
    public void testSetDelayTimeLevel() {
        final Message message = new Message(FAKE_TOPIC_0, FAKE_TAG_0, RandomUtils.nextBytes(1));
        final String oldMsgId = message.getMsgId();
        message.setDelayTimeLevel(1);
        final String newMsgId = message.getMsgId();
        assertNotEquals(oldMsgId, newMsgId);
        assertEquals(message.getDelayTimeLevel(), 1);
    }

    @Test
    public void testToString() {
        final Message message = new Message(FAKE_TOPIC_0, FAKE_TAG_0, RandomUtils.nextBytes(1));
        assertFalse(message.toString().isEmpty());
    }
}