# -*- coding: utf-8 -*-
# =====================
# Genie Tool 主服务入口
# 提供代码解释器、深度搜索、报告生成等工具服务
# Author: liumin.423
# Date:   2025/7/7
# =====================
import os
from optparse import OptionParser
from pathlib import Path

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from loguru import logger
from starlette.middleware.cors import CORSMiddleware

from genie_tool.util.middleware_util import UnknownException, HTTPProcessTimeMiddleware
from genie_tool.db.db_engine import init_db

load_dotenv()  # 加载环境变量


def print_logo():
    """打印ASCII艺术Logo"""
    from pyfiglet import Figlet
    f = Figlet(font="slant")
    print(f.renderText("Genie Tool"))


def log_setting():
    """配置日志系统"""
    log_path = os.getenv("LOG_PATH", Path(__file__).resolve().parent / "logs" / "server.log")
    log_format = "{time:YYYY-MM-DD HH:mm:ss.SSS} {level} {module}.{function} {message}"
    logger.add(log_path, format=log_format, rotation="200 MB")  # 日志文件200MB轮转


def create_app() -> FastAPI:
    """创建FastAPI应用实例"""
    _app = FastAPI(
        on_startup=[init_db, log_setting, print_logo]  # 启动时初始化数据库、日志、Logo
    )

    register_middleware(_app)
    register_router(_app)

    return _app

def register_middleware(app: FastAPI):
    """注册中间件"""
    app.add_middleware(UnknownException)  # 异常处理中间件
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],  # 允许所有来源
        allow_methods=["*"],  # 允许所有方法
        allow_headers=["*"],  # 允许所有头
        allow_credentials=True,
    )
    app.add_middleware(HTTPProcessTimeMiddleware)  # 请求耗时中间件


def register_router(app: FastAPI):
    """注册路由"""
    from genie_tool.api import api_router
    app.include_router(api_router)


app = create_app()  # 创建应用实例


if __name__ == "__main__":
    """主函数入口"""
    parser = OptionParser()
    parser.add_option("--host", dest="host", type="string", default="0.0.0.0")
    parser.add_option("--port", dest="port", type="int", default=1601)
    parser.add_option("--workers", dest="workers", type="int", default=10)
    (options, args) = parser.parse_args()

    print(f"Start params: {options}")

    uvicorn.run(
        app="server:app",
        host=options.host,
        port=options.port,
        workers=options.workers,
        reload=os.getenv("ENV", "local") == "local",  # 本地环境启用热重载
    )
