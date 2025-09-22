package com.jd.genie.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件信息数据传输对象
 *
 * 该类用于封装智能体执行过程中产生或处理的文件信息，
 * 支持文件的多地址访问、本地和云端存储，以及文件分类管理。
 * 在智能体的工作流程中，文件对象用于跟踪和管理各种输出产物。
 *
 * 主要应用场景：
 * 1. 智能体执行结果的文件输出
 * 2. 用户上传文件的元信息管理
 * 3. 工具执行产生的中间文件跟踪
 * 4. 文件的本地和云端地址映射
 *
 * @author 系统生成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class File {

    /**
     * OSS云端访问URL
     * 文件在对象存储服务(OSS)中的完整访问地址
     * 用于云端文件的高速访问和分发
     * 格式：https://bucket-name.oss-cn-region.aliyuncs.com/path/to/file
     */
    private String ossUrl;

    /**
     * 域名访问URL
     * 通过自定义域名访问文件的URL地址
     * 提供更友好的访问地址，通常用于前端展示
     * 格式：https://cdn.example.com/path/to/file
     */
    private String domainUrl;

    /**
     * 文件名
     * 文件的原始名称，包含文件扩展名
     * 用于文件识别和用户界面展示
     * 示例：report.pdf, data.xlsx, image.png
     */
    private String fileName;

    /**
     * 文件大小（字节）
     * 文件的实际大小，以字节为单位
     * 用于文件大小验证、存储容量计算和用户提示
     * 注意：Integer类型限制最大支持约2GB文件
     */
    private Integer fileSize;

    /**
     * 文件描述
     * 对文件内容和用途的文字描述
     * 帮助用户理解文件的内容和价值
     * 可用于智能体的文件选择和推荐
     */
    private String description;

    /**
     * 原始文件名
     * 用户上传时的原始文件名称
     * 保留用户的原始命名，便于文件追溯
     * 可能与fileName不同（系统可能重命名）
     */
    private String originFileName;

    /**
     * 原始OSS URL
     * 文件最初上传到OSS时的原始URL
     * 用于文件版本控制和历史追溯
     * 在文件更新时保留原始版本信息
     */
    private String originOssUrl;

    /**
     * 原始域名URL
     * 文件最初的域名访问地址
     * 配合originOssUrl使用，用于完整的文件历史追踪
     * 支持文件的版本管理和回滚操作
     */
    private String originDomainUrl;

    /**
     * 是否为内部文件
     * 标记文件是否为系统内部使用的中间产物
     * true: 内部文件，不对外展示（如临时处理文件、缓存文件）
     * false: 正式文件，对用户可见的最终输出
     *
     * 业务逻辑：
     * - 在文件列表过滤时，内部文件通常会被隐藏
     * - 在结果汇总时，内部文件不会计入最终交付物
     * - 在文件清理时，内部文件可以被优先删除
     */
    private Boolean isInternalFile;
}