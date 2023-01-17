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
package com.alibaba.dubbo.remoting.zookeeper.support;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractZookeeperClient<TargetChildListener> implements ZookeeperClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractZookeeperClient.class);

    /**
     * 注册中心
     */
    private final URL url;

    /**
     * StateListener 集合
     */
    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<StateListener>();

    /**
     * ChildListener 集合
     */
    private final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<String, ConcurrentMap<ChildListener, TargetChildListener>>();

    // 是否关闭
    private volatile boolean closed = false;

    private final Set<String>  persistentExistNodePath = new ConcurrentHashSet<String>();

    public AbstractZookeeperClient(URL url) {
        this.url = url;
    }

    @Override
    public URL getUrl() {
        return url;
    }


    @Override
    public void delete(String path){
        //never mind if ephemeral
        // 如果是临时实例也没关系,执行删除
        persistentExistNodePath.remove(path);
        deletePath(path);
    }


    @Override
    public void create(String path, boolean ephemeral) {
        // 非持久节点
        if (!ephemeral) {
            // persistentExistNodePath记录一下
            if(persistentExistNodePath.contains(path)){
                return;
            }
            if (checkExists(path)) {
                persistentExistNodePath.add(path);
                return;
            }
        }
        // 循环创建持久的父路径
        int i = path.lastIndexOf('/');
        if (i > 0) {
            create(path.substring(0, i), false);
        }
        // 创建临时节点
        if (ephemeral) {
            createEphemeral(path);
        } else {
            // 创建持久节点
            createPersistent(path);
            persistentExistNodePath.add(path);

        }
    }

    @Override
    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }

    @Override
    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public Set<StateListener> getSessionListeners() {
        return stateListeners;
    }

    @Override
    public List<String> addChildListener(String path, final ChildListener listener) {
        // 获得路径下的监听器数组
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners == null) {
            childListeners.putIfAbsent(path, new ConcurrentHashMap<ChildListener, TargetChildListener>());
            listeners = childListeners.get(path);
        }
        // 获得是否已经有该监听器
        TargetChildListener targetListener = listeners.get(listener);
        if (targetListener == null) {
            // 监听器不存在，进行创建
            listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
            targetListener = listeners.get(listener);
        }
        // 在添加监听器之后会返回监听path当前的所有的子节点
        return addTargetChildListener(path, targetListener);
    }

    @Override
    public void removeChildListener(String path, ChildListener listener) {
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners != null) {
            TargetChildListener targetListener = listeners.remove(listener);
            if (targetListener != null) {
                // 向 Zookeeper ，真正发起取消订阅
                removeTargetChildListener(path, targetListener);
            }
        }
    }

    protected void stateChanged(int state) {
        for (StateListener sessionListener : getSessionListeners()) {
            sessionListener.stateChanged(state);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            doClose();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    // 关闭
    protected abstract void doClose();

    // 创建持久节点
    protected abstract void createPersistent(String path);

    // 创建临时节点
    protected abstract void createEphemeral(String path);

    // 节点是否存在
    protected abstract boolean checkExists(String path);

    // 创建真正的 ChildListener 对象
    protected abstract TargetChildListener createTargetChildListener(String path, ChildListener listener);

    // 向 Zookeeper ，真正发起订阅
    protected abstract List<String> addTargetChildListener(String path, TargetChildListener listener);

    protected abstract void removeTargetChildListener(String path, TargetChildListener listener);

    /**
     * we invoke the zookeeper client to delete the node
     * @param path the node path
     */
    protected abstract void deletePath(String path);
}
