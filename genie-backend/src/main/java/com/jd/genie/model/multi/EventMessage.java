package com.jd.genie.model.multi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 单条事件（流式/回放）消息模型
 * - taskId/taskOrder：任务与步骤序号
 * - messageType：消息类型（task/tool/html/file/...）
 * - resultMap：消息负载（结构化）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;        // 任务ID
    private Integer taskOrder;    // 任务序号（自增）
    private String messageId;     // 消息ID
    private String messageType;   // 消息类型：task/tool/html/file/...
    private Integer messageOrder; // 同类型消息内的序号
    private Object resultMap;     // 结构化负载
}
