package com.jd.genie.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件元信息
 * - fileName/fileDesc：文件名与描述
 * - ossUrl/domainUrl：下载访问地址
 * - origin*：原始文件信息（如从外部同步而来）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInformation {
    private String fileName; // 文件名（含后缀）
    private String fileDesc; // 文件描述（20字以内摘要）
    private String ossUrl; // OSS下载地址
    private String domainUrl; // 域名访问地址
    private Integer fileSize; // 文件大小（字节）
    private String fileType; // 文件类型（md/html/csv/ppt...）
    private String originFileName; // 源文件名（原始来源）
    private String originFileUrl; // 源文件URL
    private String originOssUrl; // 源文件OSS地址
    private String originDomainUrl; // 源文件域名访问地址
}
