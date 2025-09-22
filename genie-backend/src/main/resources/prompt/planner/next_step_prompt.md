工具planing的参数有  
必填参数1：命令command  
可选参数2：当前步状态step_status。  

必填参数1：命令command的枚举值有：  
'mark_step', 'finish'  
含义如下：  

- 'finish' 根据已有的执行结果，可以判断出任务已经完成，输出任务结束，命令command为：finish  
- 'mark_step' 标记当前任务规划的状态，设置当前任务的step_status  

当参数command值为mark_step时，需要可选参数2step_status，其中当前步状态step_status的枚举值如下：  
- 没有开始 'not_started'  
- 进行中 'in_progress'   
- 已完成 'completed'  

对应如下几种情况：  
1. 当前任务是否执行完成，完成以及失败都算执行完成，执行完成将入参step_status设置为 `completed`  

一步一步分析完成任务，确定工具planing的入参，调用planing工具
