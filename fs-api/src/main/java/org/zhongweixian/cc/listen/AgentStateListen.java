package org.zhongweixian.cc.listen;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.cti.cc.constant.Constants;
import org.cti.cc.po.AgentInfo;
import org.cti.cc.po.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.zhongweixian.cc.cache.CacheService;
import org.zhongweixian.cc.websocket.WebSocketHandler;
import org.zhongweixian.cc.websocket.response.WsResponseEntity;

/**
 * Create by caoliang on 2020/11/1
 */
@Component
public class AgentStateListen {
    private Logger logger = LoggerFactory.getLogger(AgentStateListen.class);


    @Autowired
    private CacheService cacheService;

    @Autowired
    private WebSocketHandler webSocketHandler;

    @Value("${server.address}:${server.port}")
    private String host;


    /**
     * 指定监听，绑定队列队列的路由和路由
     *
     * @param payload
     */
    @RabbitListener(bindings = {@QueueBinding(value = @Queue(value = "sync.agent-" + "${server.address}:${server.port}", autoDelete = "true"), key = Constants.DEFAULT_KEY, exchange = @Exchange(value = Constants.AGENT_STATE_EXCHANGE, type = ExchangeTypes.TOPIC))})
    public void listenAgentState(@Payload String payload) {
        JSONObject json = JSON.parseObject(payload);
        if (host.equals(json.getString("host"))) {
            return;
        }
        AgentInfo agentInfo = cacheService.getAgentInfo(json.getString("agentKey"));
        if (agentInfo == null) {
            logger.info("receive agent {} change state {}", json.getString("agentKey"), json.getString("agentState"));
            agentInfo = JSON.parseObject(payload, AgentInfo.class);
            cacheService.addAgentInfo(agentInfo);
            return;
        }
        String state = json.getString("agentState");
        /**
         * 别的服务上坐席下线，本服务上坐席不做处理
         */
        if (host.equals(agentInfo.getHost()) && "LOGOUT".equals(state)) {
            return;
        }
        if (agentInfo.getHost().equals(json.getString("host"))) {
            agentInfo = JSON.parseObject(payload, AgentInfo.class);
            logger.info("receive agent {} change state {}", agentInfo.getAgentKey(), agentInfo.getAgentState());
            cacheService.addAgentInfo(agentInfo);
            return;
        } else if (agentInfo.getHost().equals(host)) {
            //旧坐席退出
            logger.info("agent:{} login on server:{}", agentInfo.getAgentKey(), agentInfo.getHost());
            String logoutResponse = JSON.toJSONString(new WsResponseEntity<>(1005, "坐席在别处登录", AgentState.LOGOUT.name(), agentInfo.getAgentKey()));
            webSocketHandler.sendMessgae(agentInfo.getAgentKey(), agentInfo.getRemoteAddress(), logoutResponse);
            webSocketHandler.close(agentInfo.getAgentKey());
            agentInfo.setHost(json.getString("host"));
            return;
        }
    }
}
