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
package com.alibaba.dubbo.rpc.cluster.router.tag;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.router.AbstractRouter;

import java.util.ArrayList;
import java.util.List;

/**
 * TagRouter
 */
public class TagRouter extends AbstractRouter {

    private static final int DEFAULT_PRIORITY = 100;
    private static final URL ROUTER_URL = new URL("tag", Constants.ANYHOST_VALUE, 0, Constants.ANY_VALUE).addParameters(Constants.RUNTIME_KEY, "true");

    public TagRouter() {
        this.url = ROUTER_URL;
        this.priority = url.getParameter(Constants.PRIORITY_KEY, DEFAULT_PRIORITY);
    }

    @Override
    public URL getUrl() {
        return url;
    }

    /**
     * tag区分，应该是指多机房处理
     * @param invokers
     * @param url        refer url
     * @param invocation
     * @param <T>
     * @return
     * @throws RpcException
     */
    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        // filter
        List<Invoker<T>> result = new ArrayList<Invoker<T>>();
        // Dynamic param
        // 获取线程本地变量tag属性，不为空则和提供者的url中的tag进行比较，相同的加入到同一个列表返回，适用于同机房调用的场景
        String tag = RpcContext.getContext().getAttachment(Constants.TAG_KEY);
        // Tag request
        if (!StringUtils.isEmpty(tag)) {
            // Select tag invokers first
            for (Invoker<T> invoker : invokers) {
                if (tag.equals(invoker.getUrl().getParameter(Constants.TAG_KEY))) {
                    result.add(invoker);
                }
            }
        }
        // If Constants.REQUEST_TAG_KEY unspecified or no invoker be selected, downgrade to normal invokers
        // 若同tag数据为空
        if (result.isEmpty()) {
            // Only forceTag = true force match, otherwise downgrade
            // 获取线程中是否是强制使用的tag,若不是强制使用，那么获取tag为空的服务列表返回，兜底降级
            String forceTag = RpcContext.getContext().getAttachment(Constants.FORCE_USE_TAG);
            if (StringUtils.isEmpty(forceTag) || "false".equals(forceTag)) {
                for (Invoker<T> invoker : invokers) {
                    if (StringUtils.isEmpty(invoker.getUrl().getParameter(Constants.TAG_KEY))) {
                        result.add(invoker);
                    }
                }
            }
        }
        return result;
    }

}
