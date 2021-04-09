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
package org.apache.rocketmq.common.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.rocketmq.common.MixAll;

public class MessageBatch extends Message implements Iterable<Message> {

    private static final long serialVersionUID = 621335151046335557L;
    //存储多条消息，其他字段和普通消息体一致
    private final List<Message> messages;

    private MessageBatch(List<Message> messages) {
        this.messages = messages;
    }

    public byte[] encode() {
        return MessageDecoder.encodeMessages(messages);
    }

    public Iterator<Message> iterator() {
        return messages.iterator();
    }

    public static MessageBatch generateFromList(Collection<Message> messages) {
        assert messages != null;
        assert messages.size() > 0;
        List<Message> messageList = new ArrayList<Message>(messages.size());
        Message first = null;
        //批量发送有几个前提必须满足，在此处进行校验
        for (Message message : messages) {
            if (message.getDelayTimeLevel() > 0) {
                throw new UnsupportedOperationException("TimeDelayLevel is not supported for batching");
            }
            if (message.getTopic().startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) {
                throw new UnsupportedOperationException("Retry Group is not supported for batching");
            }
            if (first == null) {
                first = message;
            } else {
                //批量发送的消息，必须是发送到同一个 topic
                if (!first.getTopic().equals(message.getTopic())) {
                    throw new UnsupportedOperationException("The topic of the messages in one batch should be the same");
                }
                //批量发送的消息，在是否等待刷盘的选择上，必须一致（不能有的要等，有的不等）
                if (first.isWaitStoreMsgOK() != message.isWaitStoreMsgOK()) {
                    throw new UnsupportedOperationException("The waitStoreMsgOK of the messages in one batch should the same");
                }
            }
            messageList.add(message);
        }
        MessageBatch messageBatch = new MessageBatch(messageList);

        messageBatch.setTopic(first.getTopic());
        messageBatch.setWaitStoreMsgOK(first.isWaitStoreMsgOK());
        return messageBatch;
    }

}
