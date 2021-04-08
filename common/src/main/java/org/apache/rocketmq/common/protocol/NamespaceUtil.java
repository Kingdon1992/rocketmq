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
package org.apache.rocketmq.common.protocol;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.topic.TopicValidator;

public class NamespaceUtil {
    public static final char NAMESPACE_SEPARATOR = '%';
    public static final String STRING_BLANK = "";
    public static final int RETRY_PREFIX_LENGTH = MixAll.RETRY_GROUP_TOPIC_PREFIX.length();
    public static final int DLQ_PREFIX_LENGTH = MixAll.DLQ_GROUP_TOPIC_PREFIX.length();

    /**
     * Unpack namespace from resource, just like:
     * (1) MQ_INST_XX%Topic_XXX --> Topic_XXX
     * (2) %RETRY%MQ_INST_XX%GID_XXX --> %RETRY%GID_XXX
     *
     * @param resourceWithNamespace, topic/groupId with namespace.
     * @return topic/groupId without namespace.
     */
    public static String withoutNamespace(String resourceWithNamespace) {
        if (StringUtils.isEmpty(resourceWithNamespace) || isSystemResource(resourceWithNamespace)) {
            return resourceWithNamespace;
        }

        StringBuilder stringBuilder = new StringBuilder();
        if (isRetryTopic(resourceWithNamespace)) {
            stringBuilder.append(MixAll.RETRY_GROUP_TOPIC_PREFIX);
        }
        if (isDLQTopic(resourceWithNamespace)) {
            stringBuilder.append(MixAll.DLQ_GROUP_TOPIC_PREFIX);
        }

        String resourceWithoutRetryAndDLQ = withOutRetryAndDLQ(resourceWithNamespace);
        int index = resourceWithoutRetryAndDLQ.indexOf(NAMESPACE_SEPARATOR);
        if (index > 0) {
            String resourceWithoutNamespace = resourceWithoutRetryAndDLQ.substring(index + 1);
            return stringBuilder.append(resourceWithoutNamespace).toString();
        }

        return resourceWithNamespace;
    }

    /**
     * If resource contains the namespace, unpack namespace from resource, just like:
     * (1) (MQ_INST_XX1%Topic_XXX1, MQ_INST_XX1) --> Topic_XXX1
     * (2) (MQ_INST_XX2%Topic_XXX2, NULL) --> MQ_INST_XX2%Topic_XXX2
     * (3) (%RETRY%MQ_INST_XX1%GID_XXX1, MQ_INST_XX1) --> %RETRY%GID_XXX1
     * (4) (%RETRY%MQ_INST_XX2%GID_XXX2, MQ_INST_XX3) --> %RETRY%MQ_INST_XX2%GID_XXX2
     *
     * @param resourceWithNamespace, topic/groupId with namespace.
     * @param namespace, namespace to be unpacked.
     * @return topic/groupId without namespace.
     */
    public static String withoutNamespace(String resourceWithNamespace, String namespace) {
        if (StringUtils.isEmpty(resourceWithNamespace) || StringUtils.isEmpty(namespace)) {
            return resourceWithNamespace;
        }

        String resourceWithoutRetryAndDLQ = withOutRetryAndDLQ(resourceWithNamespace);
        if (resourceWithoutRetryAndDLQ.startsWith(namespace + NAMESPACE_SEPARATOR)) {
            return withoutNamespace(resourceWithNamespace);
        }

        return resourceWithNamespace;
    }

    public static String wrapNamespace(String namespace, String resourceWithOutNamespace) {
        if (StringUtils.isEmpty(namespace) || StringUtils.isEmpty(resourceWithOutNamespace)) {
            return resourceWithOutNamespace;
        }

        if (isSystemResource(resourceWithOutNamespace) || isAlreadyWithNamespace(resourceWithOutNamespace, namespace)) {
            return resourceWithOutNamespace;
        }

        String resourceWithoutRetryAndDLQ = withOutRetryAndDLQ(resourceWithOutNamespace);
        StringBuilder stringBuilder = new StringBuilder();

        if (isRetryTopic(resourceWithOutNamespace)) {
            stringBuilder.append(MixAll.RETRY_GROUP_TOPIC_PREFIX);
        }

        if (isDLQTopic(resourceWithOutNamespace)) {
            stringBuilder.append(MixAll.DLQ_GROUP_TOPIC_PREFIX);
        }

        /*
         * 字段排序优先级
         * ①DLQ、RETRY
         * ②namespace
         * ③原始 topic
         */
        return stringBuilder.append(namespace).append(NAMESPACE_SEPARATOR).append(resourceWithoutRetryAndDLQ).toString();

    }

    public static boolean isAlreadyWithNamespace(String resource, String namespace) {
        if (StringUtils.isEmpty(namespace) || StringUtils.isEmpty(resource) || isSystemResource(resource)) {
            return false;
        }

        String resourceWithoutRetryAndDLQ = withOutRetryAndDLQ(resource);

        return resourceWithoutRetryAndDLQ.startsWith(namespace + NAMESPACE_SEPARATOR);
    }

    public static String wrapNamespaceAndRetry(String namespace, String consumerGroup) {
        if (StringUtils.isEmpty(consumerGroup)) {
            return null;
        }

        return new StringBuffer()
            .append(MixAll.RETRY_GROUP_TOPIC_PREFIX)
            .append(wrapNamespace(namespace, consumerGroup))
            .toString();
    }

    public static String getNamespaceFromResource(String resource) {
        if (StringUtils.isEmpty(resource) || isSystemResource(resource)) {
            return STRING_BLANK;
        }
        String resourceWithoutRetryAndDLQ = withOutRetryAndDLQ(resource);
        int index = resourceWithoutRetryAndDLQ.indexOf(NAMESPACE_SEPARATOR);

        return index > 0 ? resourceWithoutRetryAndDLQ.substring(0, index) : STRING_BLANK;
    }

    /**
     * 将带有"%RETRY%"、"%DLQ%"开头的 topic 名称重置
     */
    private static String withOutRetryAndDLQ(String originalResource) {
        if (StringUtils.isEmpty(originalResource)) {
            return STRING_BLANK;
        }
        if (isRetryTopic(originalResource)) {
            return originalResource.substring(RETRY_PREFIX_LENGTH);
        }

        if (isDLQTopic(originalResource)) {
            return originalResource.substring(DLQ_PREFIX_LENGTH);
        }

        return originalResource;
    }

    /**
     * 判断目标 topic 是不是系统自带的 topic
     * - 以 "rmq_sys_" 开头
     * - 在 TopicValidator.SYSTEM_TOPIC_SET 集合中
     * - 以 "CID_RMQ_SYS_" 开头
     */
    private static boolean isSystemResource(String resource) {
        if (StringUtils.isEmpty(resource)) {
            return false;
        }

        if (TopicValidator.isSystemTopic(resource) || MixAll.isSysConsumerGroup(resource)) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否为重试的 topic
     * - 以 "%RETRY%" 开头
     */
    public static boolean isRetryTopic(String resource) {
        return StringUtils.isNotBlank(resource) && resource.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX);
    }

    /**
     * 判断是否为DLQ的 topic
     * - 以 "%DLQ%" 开头
     */
    public static boolean isDLQTopic(String resource) {
        return StringUtils.isNotBlank(resource) && resource.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX);
    }
}