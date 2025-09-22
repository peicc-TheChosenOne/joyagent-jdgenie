# Tools Configuration

## Plan Tool (`plan_tool`)

**Description**:
这是一个计划工具，可让代理创建和管理用于解决复杂任务的计划。
该工具提供创建计划、更新计划步骤和跟踪进度的功能。

创建计划时时，需要创建出有依赖关系的计划，计划列表格式如下：
[
 执行顺序+编号、任务短标题：任务的细节描述
]，样式示例如下：["执行顺序1. 任务短标题: 任务描述xxx ...", "执行顺序1. 任务短标题: 任务描述xxx ...", "执行顺序2. 任务短标题：任务描述xxx ..." , "执行顺序3. 任务短标题：任务描述xxx ... "]

**Parameters Schema**:
```json
{
  "type": "object",
  "properties": {
    "step_status": {
      "description": "每一个子任务的状态. 当command是 mark_step 时使用.",
      "type": "string",
      "enum": ["not_started", "in_progress", "completed", "blocked"]
    },
    "step_notes": {
      "description": "每一个子任务的的备注，当command 是 mark_step 时，是备选参数。",
      "type": "string"
    },
    "step_index": {
      "description": "当command 是 mark_step 时，是必填参数.",
      "type": "integer"
    },
    "title": {
      "description": "任务的标题，当command是create时，是极填参数，如果是update 则是选填参数。",
      "type": "string"
    },
    "steps": {
      "description": "入参是任务列表. 当创建任务时，command是create，此时这个参数是必填参数。任务列表的的格式如下：[\"执行顺序 + 编号、执行任务简称：执行任务的细节描述\"]。不同的子任务之间不能重复、也不能交叠，可以收集多个方面的信息，收集信息、查询数据等此类多次工具调用，是可以并行的任务。具体的格式示例如下：- 任务列表示例1: [\"极行顺序1. 执行任务简称（不超过6个字）：执行任务的细节描述（不超过50个字）\", \"执行顺序2. xxx（不超过6个字）：xxx（不超过50个字）, ...\"]；",
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "command": {
      "description": "需要执行的命令，取值范围是: create, update, mark_step",
      "type": "string",
      "enum": ["create", "update", "mark_step"]
    }
  },
  "required": ["command"]
}
```

---

## Code Agent (`code_agent`)

**Description**:
这是一个Code interpreter工具，可以写Python代码

- 严禁用此工具进行处理从非表格文件中提取表格、抽取数据、抽取指标等任务。

- 严禁处理纯文本文件，例如 .txt, .md , .html 等文件的直接处理。如果需要对这些文件分析，则应该先通过读取这些文件内容，保存成 .csv 文件格式的数据表后进行处理。

- 如果上下文中有.xlsx 、.csv 等Excel表格文件需要处理分析，可以直接使用此工具读取 .xlsx 、.csv 等Excel表格文件进行分析处理

**Parameters Schema**:
```json
{
  "type": "object",
  "properties": {
    "task": {
      "description": "任务的描述，不仅包括提供任务目标、任务的相关要求，还包括详细的完成任务需要的细节信息。详细是指不仅包含上下文中提及的所有与任务的相关内容，同时，包括：用户提供的业务名词、业务背景、数据等相关内容，确保这是一个易于理解、步骤明确、且能完成完整的任务描述。完整任务的定义是所有依赖项都在这里写清楚，明确无歧义，信息无丢失，基于这些信息足够完成该任务。禁止编造数据，写出来的程序是基于上下文已有的数据进行。",
      "type": "string"
    }
  },
  "required": ["task"]
}
```

---

## Report Tool (`report_tool`)

**Description**:
这是一个专业的Markdown、PPT和HTML的生成工具，可以用于输出 html 格式的网页报告、PPT 或者 markdown 极式的报告，生成报告或者需要输出 Markdown 或者 HTML 或者 PPT 格式时，一定使用此工具生成报告。（如果没有明确的格式要求默认生成 Markdown 格式的），不要重复生成相同、类似的报告。这不是查数工具，严禁用此工具查询数据。不同入参之间都应该是跟任务强相关的，同时文件名称、文件描述和任务描述之间都应该是围绕完成任务目标生成的。

**Parameters Schema**:
```json
{
  "type": "object",
 极 "properties": {
    "fileDescription": {
      "description": "生成报告的文件描述，一定是跟用户的任务强相关的",
      "type": "string"
    },
    "fileName": {
      "description": "生成的文件名称，文件的前缀中文名称一定是跟用户的任务和文件内容强相关，如果是markdown，则文件名称后缀是 .md，如果是ppt、html文件，则是文件名称后缀是 .html，一定要包含文件后缀。文件名称不能使用特殊符号，不能使用、，？等符号，如果需要，可以使用下划线_。",
      "type": "string"
    },
    "task": {
      "description": "生成文件任务的具体要求及详细描述，以及需要在报告中体现的内容，例如，上下文中需要输出的数据细节。",
      "type": "string"
    },
    "fileType": {
      "description": "仅支持 markdown ppt 和 html 三种类型，如果指定了输出 html 或 网页版 格式，则是html，如果指定了输出 ppt、pptx 则是 ppt，否则使用 markdown 。",
      "type": "string"
    }
  },
  "required": ["fileType", "task", "fileName", "fileDescription"]
}
```

---

## File Tool (`file_tool`)

**Description**:
这是一个文件读写的工具，支持写文件操作upload和获取文件操作get的命令，不支持写入以 .xlsx 为后缀的格式文件。不擅长写报告的HTML和Markdown类型文件，当有这些类型的文件需要写入或保存时，优先使用其它工具。

- 当获取了大量的内容的时候，将内容的核心数据和结果进行总结，写入到文件里保存起来。

- 在需要的时候，执行文件获取操作get，读取文件内容。

- 当需要将以 .txt、 .md 、.html 为后缀的文件中的表格、数据、指标提取出来时，可以使用 upload 将文件中提供的表格数据保存成csv文件。

- 将搜索、查询数据的结果进行文件写入操作upload。

- 不支持读取以 .xlsx 后缀的文件，禁止使用该工具读取 .极lsx 后缀的文件，但支持写入Excel 格式中的以.csv作为文件后缀的文件。

- 不支持直接读取.png，.img，.jpg，.doc，.pdf，.ppt 诸如此类的非平文本类文件，只能支持.txt、 .md 、.html这类平文本文件内容读取

**Parameters Schema**:
```json
{
  "type": "object",
  "properties": {
    "filename": {
      "description": "文件名一定是中文名称，文件名后缀取决于准备写入的文件内容，如果内容是Markdown格式排版的内容，则文件名的后缀是.md结尾。读取文件时，一定是历史对话中已经写入的文件名称。所有文件名称都需要唯一。文件名称中不能使用特殊符号，不能使用、，？等符号，如果需要，可以使用下划线_。需要写入数据表格类的文件时，以 .csv 文件为后缀。纯文本文件优先使用 Markdown 文件保存，不要使用 .txt 保存文件。不支持.pdf、.png、.zip为后缀的文件读写。",
      "type": "string"
    },
    "description": {
      "description": "文件描述，用20字左右概括该极件内容的主要内容及用途，当command是upload时，属于必填参数",
      "type": "string"
    },
    "command": {
      "description": "文件操作类型枚举值包含upload和get两种操作命令，含义分别是upload：表示上传、get表示文件下载，相当于读文件操作",
      "type": "string"
    },
    "content": {
      "description": "这是需要写入的文件内容，当command是upload时，属于必填参数。",
      "type": "string"
    }
  },
  "required": ["command", "filename"]
}
```

**Additional Config**:
- `truncate_len`: 30000

---

## Deep Search Tool (`deep_search_tool`)

**Description**:
这是一个搜索工具，可以搜索各种互联网知识

**Parameters Schema**:
```json
{}
```

---

## Deep Search (`deep_search`)

**Description**:
这是一个搜索工具，可以搜索各种互联网知识

**Parameters Schema**:
```json
{
  "type": "object",
  "properties": {
    "query": {
      "description": "需要搜索的全部内容及描述",
      "type": "string"
    }
  },
  "required": ["query"]
}
```

**Additional Config**:
- `page_count`: 5
- `src_config`: `{}`
- `message.truncate_len`: 20000
- `file_desc.truncate_len`: 1500

---

## Utility Configurations

- `task_complete_desc`: "当前task完成，请将当前task标记为 completed"
- `clear_tool_message`: 1