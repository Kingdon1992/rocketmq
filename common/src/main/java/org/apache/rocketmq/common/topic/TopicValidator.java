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
package org.apache.rocketmq.common.topic;

import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检测 topic 名称合法性的工具类
 *
 * ① 规定了一些常量值，包括
 *   - 默认自动生成的 topic 名称
 *   - topic 的合法字符 0~9 a~z A~Z | % - _
 *   - 部分系统自带的 topic 名称
 *   - 其他系统走到的 topic 名称的前缀 rmq_sys_
 *   - topic 名称默认最大长度127
 * ② 持有两个集合
 *   - 系统 topic 集合
 *   - 禁止接收信息 topic 集合
 */
public class TopicValidator {

    public static final String AUTO_CREATE_TOPIC_KEY_TOPIC = "TBW102"; // Will be created at broker when isAutoCreateTopicEnable
    public static final String RMQ_SYS_SCHEDULE_TOPIC = "SCHEDULE_TOPIC_XXXX";
    public static final String RMQ_SYS_BENCHMARK_TOPIC = "BenchmarkTest";
    public static final String RMQ_SYS_TRANS_HALF_TOPIC = "RMQ_SYS_TRANS_HALF_TOPIC";
    public static final String RMQ_SYS_TRACE_TOPIC = "RMQ_SYS_TRACE_TOPIC";
    public static final String RMQ_SYS_TRANS_OP_HALF_TOPIC = "RMQ_SYS_TRANS_OP_HALF_TOPIC";
    public static final String RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC = "TRANS_CHECK_MAX_TIME_TOPIC";
    public static final String RMQ_SYS_SELF_TEST_TOPIC = "SELF_TEST_TOPIC";
    public static final String RMQ_SYS_OFFSET_MOVED_EVENT = "OFFSET_MOVED_EVENT";

    public static final String SYSTEM_TOPIC_PREFIX = "rmq_sys_";

    private static final String VALID_PATTERN_STR = "^[%|a-zA-Z0-9_-]+$";
    private static final Pattern PATTERN = Pattern.compile(VALID_PATTERN_STR);
    private static final int TOPIC_MAX_LENGTH = 127;

    private static final Set<String> SYSTEM_TOPIC_SET = new HashSet<String>();

    /**
     * Topics'set which client can not send msg!
     */
    private static final Set<String> NOT_ALLOWED_SEND_TOPIC_SET = new HashSet<String>();

    static {
        SYSTEM_TOPIC_SET.add(AUTO_CREATE_TOPIC_KEY_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_SCHEDULE_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_BENCHMARK_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRANS_HALF_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRACE_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRANS_OP_HALF_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_TRANS_CHECK_MAX_TIME_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_SELF_TEST_TOPIC);
        SYSTEM_TOPIC_SET.add(RMQ_SYS_OFFSET_MOVED_EVENT);

        NOT_ALLOWED_SEND_TOPIC_SET.add(RMQ_SYS_SCHEDULE_TOPIC);
    }

    private static boolean regularExpressionMatcher(String origin, Pattern pattern) {
        if (pattern == null) {
            return true;
        }
        Matcher matcher = pattern.matcher(origin);
        return matcher.matches();
    }

    /**
     * 检查 topic 名称是否符合规范
     * ①不允许为全空格
     * ②不允许特殊字符
     * ③长度不允许超过127
     */
    public static boolean validateTopic(String topic, RemotingCommand response) {

        if (UtilAll.isBlank(topic)) {
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark("The specified topic is blank.");
            return false;
        }

        if (!regularExpressionMatcher(topic, PATTERN)) {
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark("The specified topic contains illegal characters, allowing only " + VALID_PATTERN_STR);
            return false;
        }

        if (topic.length() > TOPIC_MAX_LENGTH) {
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark("The specified topic is longer than topic max length.");
            return false;
        }

        return true;
    }

    public static boolean isSystemTopic(String topic, RemotingCommand response) {
        if (isSystemTopic(topic)) {
            response.setCode(ResponseCode.SYSTEM_ERROR);
            response.setRemark("The topic[" + topic + "] is conflict with system topic.");
            return true;
        }
        return false;
    }

    /**
     * 判断 topic 是否为系统 topic，系统 topic
     * ① 要么存在 TopicValidator.SYSTEM_TOPIC_SET 集合中
     * ② 要么以 "rmq_sys_" 开头
     */
    public static boolean isSystemTopic(String topic) {
        return SYSTEM_TOPIC_SET.contains(topic) || topic.startsWith(SYSTEM_TOPIC_PREFIX);
    }

    public static boolean isNotAllowedSendTopic(String topic) {
        return NOT_ALLOWED_SEND_TOPIC_SET.contains(topic);
    }

    public static boolean isNotAllowedSendTopic(String topic, RemotingCommand response) {
        if (isNotAllowedSendTopic(topic)) {
            response.setCode(ResponseCode.NO_PERMISSION);
            response.setRemark("Sending message to topic[" + topic + "] is forbidden.");
            return true;
        }
        return false;
    }

    public static void addSystemTopic(String systemTopic) {
        SYSTEM_TOPIC_SET.add(systemTopic);
    }

    public static Set<String> getSystemTopicSet() {
        return SYSTEM_TOPIC_SET;
    }

    public static Set<String> getNotAllowedSendTopicSet() {
        return NOT_ALLOWED_SEND_TOPIC_SET;
    }
}
