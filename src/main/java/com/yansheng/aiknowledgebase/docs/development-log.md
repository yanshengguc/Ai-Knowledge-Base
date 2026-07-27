# Day1

## 完成
- 创建 Spring Boot 项目
- 完成第一个接口 `/api/hello`
- 实现 JSON 返回

## 学习
- `@RestController`
    - 用于创建 REST 接口，返回数据
- `@RequestMapping("/api")`
    - 设置 Controller 的公共路径
- `@GetMapping("/hello")`
    - 定义具体 GET 请求接口
- Java 对象自动转换 JSON
- 构造方法的作用
- getter 方法用于读取 private 属性

## 问题
- 一开始忘记 Java 构造方法写法
- 不理解为什么 private 属性需要 getter
- 理解后知道 Spring Boot 会通过对象生成 JSON

## 今日总结
完成第一个 Spring Boot REST API，从请求到 JSON 返回流程跑通。
# Day2

## 完成
- Service层
- ServiceImpl
- Controller调用Service
- Spring依赖注入


## 核心理解

Controller：
接收请求

Service：
处理业务

Impl：
实现业务


## 记住

interface → implements

@Service → 交给Spring管理

不用new → Spring自动注入
Day3

完成：
- 创建统一返回 Result
- success/error 方法封装
- Controller统一返回格式

理解：
- common 放公共组件
- Result 是返回外壳
- data 放不同业务对象

核心：
Day2解决代码分层
Day3解决接口返回规范
# Day4：统一异常处理（Global Exception Handler）

## 今日目标

实现项目统一异常处理，让 Service 只负责业务，异常统一交给 Handler 处理。

---

## 今天新增

### 1. BusinessException（业务异常）

位置：

common/BusinessException.java

作用：

用于表示业务异常。

例如：

- 用户不存在
- 密码错误
- 库存不足

业务出错时：

```java
throw new BusinessException("用户不存在");
```

为什么继承 RuntimeException？

- 属于运行时异常
- 可以直接 throw
- 可以保存 message（super(message)）

---

### 2. GlobalExceptionHandler（全局异常处理器）

位置：

handler/GlobalExceptionHandler.java

作用：

统一处理所有 BusinessException。

使用：

```java
@RestControllerAdvice
```

表示：

Spring 自动扫描，这是全局异常处理器。

处理方法：

```java
@ExceptionHandler(BusinessException.class)
public Result businessException(BusinessException e){
    return Result.error(e.getMessage());
}
```

作用：

收到 BusinessException 后：

```text
BusinessException
        ↓
e.getMessage()
        ↓
Result.error(...)
```

最终返回：

```json
{
    "code":500,
    "message":"用户不存在",
    "data":null
}
```

---

## 为什么不用

```java
return Result.error(...)
```

而要：

```java
throw new BusinessException(...)
```

原因：

Service 负责业务。

Handler 负责返回。

实现职责分离（分层）。

---

## Day4 请求流程

正常：

浏览器
↓

Controller

↓

Service

↓

Result.success(...)

↓

浏览器

异常：

浏览器

↓

Controller

↓

Service

↓

throw BusinessException(...)

↓

GlobalExceptionHandler

↓

Result.error(...)

↓

浏览器

---

## 今天理解的核心

BusinessException

负责：

抛异常。

GlobalExceptionHandler

负责：

处理异常。

Result.error()

负责：

统一返回异常信息。

---

## Day4 验收

能够回答：

✓ 为什么不用 return Result.error()？

Service 只负责业务，异常统一交给 Handler。

✓ 为什么继承 RuntimeException？

保存异常信息，并作为运行时异常抛出。

✓ 为什么需要 GlobalExceptionHandler？

统一处理异常，避免每个 Controller 重复写 try-catch。

✓ Spring 为什么知道调用 Handler？

因为：

@RestControllerAdvice

和：

@ExceptionHandler

两个注解。

---

## 今日收获

完成了 Spring Boot 企业项目的基础异常处理框架：

Controller
↓

Service

↓

BusinessException

↓

GlobalExceptionHandler

↓

Result

↓

JSON
Day5 总结：数据库分层 + MyBatis链路

1. 新增项目结构

现在项目结构：

aiknowledgebase
├── controller
│   └── UserController
│
├── service
│   ├── UserService
│   └── impl
│       └── UserServiceImpl
│
├── mapper
│   └── UserMapper
│
├── entity
│   └── UserEntity
│
├── vo
│   └── UserVO
│
├── common
│   └── Result
│
└── handler
└── GlobalExceptionHandler


---

核心知识

1. Entity

数据库对应对象：

数据库表
↓
UserEntity

例如：

id
username
password

负责保存数据库数据。


---

2. VO

返回给前端的数据：

UserEntity
↓ 转换
UserVO
↓
前端

例如：

username

不返回：

password

避免泄露。


---

3. Mapper

负责数据库操作：

UserEntity findById(Long id);

Mapper 是接口。

原因：

> 定义数据库访问能力，具体实现由 MyBatis 生成。




---

4. Service

负责业务逻辑：

Controller
↓
Service
↓
Mapper

不要让 Controller 直接操作数据库。


---

5. 完整请求流程

访问：

/api/user/1

流程：

Controller
↓
UserService
↓
UserServiceImpl
↓
UserMapper
↓
MyBatis
↓
MySQL
↓
UserEntity
↓
UserVO
↓
Result


---

今天遇到的问题

1. UserMapper 找不到

错误：

No qualifying bean of type UserMapper

原因：

Spring 不知道 Mapper。

解决：

@MapperScan("com.yansheng.aiknowledgebase.mapper")


---

2. sqlSessionFactory 不存在

错误：

Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required

原因：

MyBatis 环境没有正确启动。

解决：

MyBatis starter版本调整

数据库配置

MySQL驱动
# Day5

## 完成内容

- 设计 Knowledge 业务模型
- 创建 KnowledgeEntity
- 创建 KnowledgeVO（列表展示）
- 创建 KnowledgeDetailVO（详情展示）

- 创建 KnowledgeMapper
- 学习 Mapper 负责数据库查询

- 创建 KnowledgeService
- 创建 KnowledgeServiceImpl
- 完成 Entity → VO 转换

- 创建 KnowledgeController
- 完成列表查询接口
- 完成详情查询接口


## 掌握

- Entity 对应数据库数据
- VO 用于返回给前端的数据
- 列表查询：
  List<Entity> → List<VO>

- 详情查询：
  Entity → DetailVO

- Service 负责业务处理和数据转换
- Controller 负责接收请求并返回结果

## 请求链路

Controller
↓
Service
↓
Mapper
↓
MySQL
↓
Entity
↓
VO
↓
Result


## 遇到问题

- 理解 Mapper 返回 Entity，而不是直接返回 VO
- 理解 Service 为什么需要 Entity 转 VO
- 区分接口和实现类调用
- 修正 Controller 返回类型
- 理解 List 和单对象转换区别


Day6: integrate MySQL with MyBatis knowledge query

完成知识库模块数据库接入：

- 配置 MySQL 数据源
- 配置 MyBatis Mapper XML 扫描
- 创建 knowledge 数据表测试数据
- 完成 KnowledgeMapper XML SQL 映射
- 接入真实数据库查询
- 完善知识库列表查询接口
- 完善知识库详情查询接口
- 完成 Entity → VO 数据转换流程

解决问题：
- Invalid bound statement (not found)
- 理解 Mapper 接口与 XML 映射关系
- 理解 MyBatis 数据查询流程

接口测试：
- GET /api/knowledge
- GET /api/knowledge/{id}

完成 Controller → Service → Mapper → MySQL 完整查询链路
---

Day7：新增（Create）

完成内容

新建 KnowledgeAddDTO，用于接收前端新增数据。

Controller 新增 POST /api/knowledge 接口。

Service 新增 addKnowledge(KnowledgeAddDTO dto)。

ServiceImpl 完成 DTO → Entity 转换。

新增 KnowledgeMapper.insert(KnowledgeEntity entity)。

编写 MyBatis insert SQL。

使用 LocalDateTime.now() 设置 createTime、updateTime。

使用 Apifox 完成 POST 请求测试。

数据成功写入 MySQL。

配置 MyBatis 驼峰映射，解决 create_time、update_time 返回 null 的问题。



---

学习内容

DTO

用于接收前端请求数据，不直接使用 Entity。

流程：

前端
↓
DTO
↓
Entity


---

新增接口流程

Apifox(JSON)
↓
Controller
↓
Service
↓
Entity
↓
Mapper
↓
Mapper.xml
↓
MySQL


---

MyBatis Insert

掌握：

insert into ... values(...)

理解：

insert 不需要 where。

values 中只写 #{}。

id 由数据库自增。



---

时间处理

新增时：

LocalDateTime now = LocalDateTime.now();

同时赋值：

createTime

updateTime



---

Apifox

第一次使用 POST + JSON 请求测试接口。

浏览器主要用于 GET，请求新增、修改、删除统一使用 Apifox。


---

驼峰映射

数据库：

create_time

Java：

createTime

配置：

mybatis.configuration.map-underscore-to-camel-case=true


---

今日踩坑

insert 与 select 写法混淆。

insert 不使用 where。

values 中不能写 title=。

Result.success() 需要改为 Result.success(null)。

浏览器不能方便测试 POST。

未开启驼峰映射导致时间字段返回 null。
---

Day8：Knowledge Update 修改接口 ✅

目标

完成 Knowledge 修改接口。

接口：

PUT /api/knowledge/{id}


---

完成内容

DTO

新增：

KnowledgeUpdateDTO

字段：

title

content

category

author


理解：

不用 Entity 接收前端数据：

防止暴露数据库字段

控制可修改内容



---

Controller

完成：

@PutMapping("/knowledge/{id}")

接收：

@PathVariable id

@RequestBody DTO


流程：

请求
↓
Controller
↓
Service


---

Service

新增：

void updateKnowledge(Long id, KnowledgeUpdateDTO dto);


---

ServiceImpl

完成：

id查询旧数据
↓
判断是否存在
↓
DTO → Entity修改
↓
更新updateTime
↓
Mapper.update()

掌握：

Update需要先查id，不是直接新增。


---

Mapper

新增：

int update(KnowledgeEntity entity);

理解：

Mapper只负责数据库操作，不处理业务。


---

XML

完成：

<update id="update">

SQL：

update knowledge
set ...
where id=#{id}

掌握：

where防止全表更新

#{}参数绑定



---

测试

Apifox PUT测试成功。

数据库修改成功。


---
Day9 总结

今天完成内容

1. Controller

新增删除接口

使用 @DeleteMapping

路径：/api/knowledge/{id}

使用 @PathVariable Long id

调用 knowledgeService.deleteKnowledge(id)

返回 Result.success(null)（根据你的 Result 实现）


2. Service

新增：


void deleteKnowledge(Long id);

3. ServiceImpl

根据 id 查询数据

数据不存在，抛出 BusinessException

调用 knowledgeMapper.delete(id)

判断影响行数

删除失败继续抛出异常


4. Mapper

新增：


int delete(Long id);

5. Mapper XML

<delete id="delete" parameterType="java.lang.Long">
    DELETE FROM knowledge
    WHERE id = #{id}
</delete>

6. 测试

删除接口完成（如果还没用 Apifox 测试，记得补测一次）



---

今天掌握的知识

DELETE 请求的使用

RESTful 删除接口设计

@PathVariable 的作用

为什么删除接口不需要 DTO

删除前先查询数据是否存在

Mapper 返回 int 表示影响行数

MyBatis <delete> 标签

parameterType="java.lang.Long"



---
Day10 总结 —— 用户注册模块

一、完成内容

1. 用户注册接口

接口：

POST /api/user/register

流程：

Controller
↓
UserRegisterDTO
↓
Service
↓
用户名查重
↓
DTO转Entity
↓
Mapper.insert
↓
MySQL


---

2. 新增 UserRegisterDTO

字段：

username
password
nickname

作用：

接收前端注册数据。


---

3. 注册业务逻辑

核心：

查询username

如果存在：
抛异常

如果不存在：
DTO → Entity
插入数据库


---

4. Mapper

新增：

UserEntity getUserByName(String username);

int insert(UserEntity userEntity);

完成：

根据用户名查询

插入用户数据



---

二、遇到的问题

1. DTO字段大小写问题

问题：

userName
nickName

导致：

username=null
nickname=null

解决：

统一：

username
nickname


---

2. DTO → Entity赋值错误

错误：

user.setUsername(user.getUsername());

原因：

新创建Entity为空。

正确：

user.setUsername(dto.getUsername());


---

3. 注册逻辑错误

错误：

register(dto);

导致递归调用。

正确：

DTO → Entity → insert


---

三、今日知识点

DTO作用

接收前端参数

隔离数据库结构

避免前端直接操作Entity


注册判断

查询业务：

null = 不存在

注册业务：

!= null = 已存在


---

四、测试结果

✅ 注册成功
✅ 重复用户名拦截
✅ 数据正常保存MySQL


---

---

Day11（完成）

User 登录模块

完成：

LoginDTO

login 接口

Controller → Service → Mapper 登录流程

复用 getUserByName()

用户不存在判断

密码校验

登录成功

Apifox 测试通过



---


Day11：User Login Module

内容：

LoginDTO

登录接口

登录业务

密码校验

登录测试成功



---

今日 Bug

Bug1

错误：

登录再次查询密码

userMapper.getUserByPassword(...)

正确：

只根据用户名查询一次：

userMapper.getUserByName(username)

密码在 Service 中比较。


---

Bug2

Controller 返回 UserEntity

容易把密码返回前端。

当前：

返回 "登录成功"。

后续：

返回 LoginVO + JWT。


---

今日重点

① Mapper 只负责数据库操作，不负责业务。

② 登录流程：

Controller

↓

LoginDTO

↓

Service

↓

Mapper

↓

UserEntity

↓

判断用户是否存在

↓

判断密码

↓

登录成功

③ 登录只查询一次数据库。


---

当前项目完成度

SpringBoot ✅

Controller ✅

Service ✅

MyBatis ✅

MySQL ✅

CRUD ✅

统一返回 ✅

统一异常 ✅

Knowledge模块 ✅

User注册 ✅

User登录 ✅


---
Day12 JWT认证总结

1. 登录流程

用户名+密码
↓
校验数据库
↓
生成JWT Token
↓
返回客户端

Token保存：

id
username
过期时间

不保存：

password


---

2. JWT作用

JWT = 用户身份凭证。

请求时：

客户端带Token
↓
服务器解析Token
↓
确认是谁

不用像Session一样保存登录状态。


---

3. JwtUtil

两个方法：

生成Token

用户信息
↓
加密签名
↓
返回Token

解析Token

Token
↓
验证签名和过期时间
↓
得到Claims


---

4. Filter流程（重点）

请求
↓
读取Authorization
↓
去掉Bearer
↓
解析JWT
↓
获取用户id、username
↓
UserContext保存用户
↓
Controller执行
↓
remove清理


---

5. UserContext + ThreadLocal

作用：

保存当前请求用户。

不用：

static UserEntity user;

因为多个用户会互相覆盖。

ThreadLocal：

线程A → 用户A
线程B → 用户B

互不影响。


---

6. 401和403

401：

没有身份
(Token错误/过期)

403：

有身份
但是没权限


---

今天完成

✅ JWT生成
✅ JWT解析
✅ Filter拦截
✅ ThreadLocal保存用户
✅ Token错误返回401
✅ 完成认证链路
Day13 总结（JWT业务权限）✅

完成内容

1. 新增知识接入JWT

之前：

author = dto.getAuthor()

问题：

前端可以伪造作者。


现在：

UserContext.get()
↓
获取当前用户
↓
author = username


---

2. 修改权限校验

修改流程：

根据id查询知识
↓
不存在 → 异常
↓
获取当前用户
↓
比较当前用户 == 作者
↓
不一致 → 权限不足
↓
修改

注意：

不修改 author

author 表示创建者



---

3. 删除权限校验

删除流程：

查询数据
↓
判断存在
↓
获取当前用户
↓
判断是否本人
↓
删除


---

核心理解

认证 Authentication

解决：

> 你是谁？



实现：

用户名密码
↓
JWT
↓
UserContext


---

授权 Authorization

解决：

> 你能不能操作？



例如：

当前用户
vs
数据作者

一致：

✅ 可以操作

不一致：

❌ 权限不足


---

今日设计思考

你提出：

更好的数据库设计：

knowledge
---------
user_id   （权限）
author    （展示昵称）

权限用 id，展示用昵称。

后期可以优化。


---

发现的问题（Day14处理）

当前 Filter：

无Token → 放行

需要改成：

login/register 放行

其他接口必须登录



---
Day14 总结（JWT认证完善）✅

今日目标

完善 JWT Filter，让接口真正具备登录保护能力。


---

1. 白名单放行

问题：

之前：

没有Token
↓
直接放行

导致所有接口都能访问。

修改：

请求
↓
获取uri
↓
判断白名单
↓
是
↓
直接放行

代码：

if (WHITE_LIST.contains(uri)) {
filterChain.doFilter(request,response);
return;
}

作用：

登录、注册不需要Token

其他接口需要认证



---

2. Token格式校验

检查：

if(token == null || !token.startsWith("Bearer "))

防止：

没有Token

Token格式错误


错误：

返回：

401 Unauthorized


---

3. JWT解析优化

解析：

Token
↓
Claims
↓
id
↓
username
↓
UserContext

增加校验：

if(id == null || username == null)

防止：

JWT字段缺失

后续业务空指针



---

4. Filter完整流程

现在：

请求
↓
获取URI
↓
白名单？
↓
是 → 放行

否
↓
获取Authorization
↓
Token不存在/格式错误
↓
401

Token正确
↓
解析JWT
↓
获取用户信息
↓
UserContext.set()

↓
Controller执行

↓
UserContext.remove()


---

Day12-Day14整体总结

Day12 JWT基础

完成：

JWT生成

JWT解析

Claims获取用户信息

Filter解析Token

ThreadLocal保存当前用户



---

Day13 JWT业务权限

完成：

新增：

JWT
↓
UserContext
↓
author自动设置

修改：

判断资源是否存在

判断当前用户是否作者


删除：

判断资源存在

判断当前用户是否作者



---

Day14 JWT认证完善

完成：

白名单机制

Token格式校验

未登录401

JWT字段校验

完整Filter流程



---

当前项目能力提升

现在项目已经具备：

✅ 用户身份识别
✅ 登录状态保持
✅ 当前用户获取
✅ 数据归属控制
✅ 基础权限保护


---

后续优化记录（暂不做）

1. AntPathMatcher



用途：

支持：

/api/user/**
/swagger/**

通配路径。


---

2. 日志系统



用途：

记录：

Token过期

Token伪造

认证失败原因



---

3. RBAC管理员权限



后续阶段加入：

USER
ADMIN

实现管理员管理所有数据。


---

状态：

Day14 ✅ 完成

Day12-Day14 JWT认证授权阶段结束。

56天 Java后端 + AI应用冲刺计划

Day15 总结 —— Docker环境搭建完成

日期：

阶段：

Phase 2 —— Redis与工程能力

目标：

完成后续 Redis、Docker部署、RAG环境所需的基础环境搭建。

---

一、今日Boss任务

Docker环境搭建

完成：

- WSL2环境准备
- Ubuntu初始化
- Docker Desktop安装
- WSL Integration配置
- Docker运行测试

最终目标：

能够在Windows + WSL2环境下正常运行Docker容器。

---

二、今日完成内容

1. WSL2环境

完成：

- Windows Subsystem for Linux安装
- Ubuntu-24.04安装
- WSL默认版本设置为2

验证：

wsl --status

结果：

默认版本：

2

---

2. Ubuntu初始化

完成：

更新软件源：

sudo apt update

升级系统：

sudo apt upgrade -y

安装基础工具：

sudo apt install curl wget git vim -y

验证：

git --version

成功返回Git版本。

---

3. Docker Desktop安装

完成：

- Docker Desktop安装
- WSL2后端启用
- Ubuntu-24.04 WSL Integration开启

Windows：

Docker Desktop

↓

Settings

↓

Resources

↓

WSL Integration

↓

开启 Ubuntu-24.04

---

三、遇到的问题

问题1：Docker权限不足

错误：

permission denied while trying to connect to the docker API at unix:///var/run/docker.sock

原因：

当前Linux用户没有访问Docker服务的权限。

解决：

加入docker用户组：

sudo usermod -aG docker $USER

刷新权限：

newgrp docker

重新执行Docker命令成功。

---

四、Docker测试

执行：

docker --version

结果：

Docker version 29.6.2

运行测试：

docker run hello-world

成功输出：

Hello from Docker!
This message shows that your installation appears to be working correctly.

证明：

Docker环境完整可用。

---

五、今日核心知识

1. Docker运行流程

Docker Client
|
↓
Docker Daemon
|
↓
Docker Hub拉取Image
|
↓
创建Container
|
↓
运行程序

---

2. Image和Container区别

Image：

Docker镜像。

理解：

«应用运行所需要的模板。»

包含：

- 程序
- 依赖
- 环境配置

例如：

redis image

Container：

Docker容器。

理解：

«镜像启动后的运行实例。»

例如：

redis image

↓

redis container

---

3. 当前Docker架构

Windows

↓

WSL2

↓

Ubuntu

↓

Docker Client

↓

Docker Desktop

↓

Docker Engine

↓

Container

Ubuntu中的docker命令负责发送请求。

真正运行容器的是Docker Engine。

---

六、今日Bug记录

Bug

docker运行hello-world失败：

permission denied

原因：

Linux用户权限不足。

解决：

加入docker用户组。

掌握：

Linux权限管理：

用户

↓

用户组

↓

权限

---

七、Git记录

今日：

无代码提交。

原因：

主要完成开发环境搭建。

---

八、面试知识

Q1：Docker Image和Container有什么区别？

回答：

Image是Docker镜像，是一个只读模板，包含应用运行所需的环境和文件。

Container是镜像启动后的实例，是实际运行的程序。

---

Q2：Docker为什么需要Image？

回答：

因为不同环境可能存在依赖差异。

Image可以将程序、环境、依赖统一打包，实现一次构建，多处运行。

---

九、Day15完成状态

环境：

✅ WSL2

✅ Ubuntu

✅ Git

✅ Docker Desktop

✅ Docker运行测试

Phase 2准备：

✅ 完成

下一阶段：

Day16 Redis基础

Boss：

使用Docker启动Redis，并让Spring Boot连接Redis。

目标：

Spring Boot

+

Redis

+

Docker

形成企业后端常见技术组合。

# Day16 —— Redis基础与Docker运行Redis

## 今日目标

使用Docker启动Redis，了解Redis基本使用，为Spring Boot整合Redis做准备。


## 完成内容

- Docker启动Redis容器
- 使用redis-cli连接Redis
- 完成Redis基本操作测试

命令：

```bash
docker run -d --name redis -p 6379:6379 redis

测试：

ping
set name yansheng
get name

核心知识

Redis

Redis是基于内存的Key-Value数据库。

特点：

速度快

适合作为缓存

减少MySQL压力


Redis缓存流程

请求：

Service
  |
Redis
  |
命中 -> 返回

未命中
  |
MySQL
  |
写入Redis

缓存一致性

更新数据：

修改MySQL
   |
删除Redis缓存
   |
下次查询重新加载
---

Day17 完成记录：Redis缓存整合

项目：

Ai-Knowledge-Base

完成内容：

1. Redis环境

Docker启动Redis

Spring Boot连接Redis


配置：

spring.data.redis.host=localhost
spring.data.redis.port=6379


---

2. RedisTemplate配置

解决：

No qualifying bean of type 'RedisTemplate'

原因：

Spring容器没有RedisTemplate Bean。

解决：

创建：

@Configuration
@Bean
RedisTemplate<String,Object>


---

3. Service层接入Redis

实现缓存旁路模式：

查询详情

 ↓

Redis查询

 ↓

有缓存
    ↓
    返回缓存

没有缓存
    ↓
    查询MySQL
    ↓
    转VO
    ↓
    写入Redis
    ↓
    返回

代码核心：

String key = "knowledge:" + id;

Object obj = redisTemplate.opsForValue().get(key);

if(obj != null){
    return (KnowledgeDetailVO)obj;
}

查询数据库后：

redisTemplate.opsForValue().set(key, vo);


---

4. Redis序列化问题解决

遇到：

Java 8 date/time type LocalDateTime not supported

原因：

Redis使用Jackson序列化VO时：

KnowledgeDetailVO

包含：

LocalDateTime createTime;
LocalDateTime updateTime;

默认Jackson无法处理。

解决：

加入：

objectMapper.registerModule(new JavaTimeModule());


---

Day17遇到的问题记录

问题1

RedisTemplate无法注入

原因： 没有配置Bean。

解决： RedisConfig。


---

问题2

登录400

原因：

请求：

GET /api/user/login

应该：

POST /api/user/login


---

问题3

详情接口401排查

最终发现不是JWT权限问题。

真实原因：

Redis写缓存时序列化异常。


---

问题4

LocalDateTime无法序列化

解决：

JavaTimeModule。


---

当前项目能力

现在项目已经从：

Controller
 ↓
Service
 ↓
Mapper
 ↓
MySQL

升级为：

Controller
 ↓
Service
 ↓
Redis
 ↓
(MySQL)
 ↓
Redis缓存

已经具备真实后端项目里的缓存设计。


---
Day18 总结：缓存一致性（Cache Aside）

恭喜你，Day18 已完成。🎉


---

一、今天完成了什么

理论

学习了 Cache Aside（旁路缓存） 写策略。

查询流程：

Redis
↓
命中
↓
直接返回

未命中
↓
MySQL
↓
写入Redis
↓
返回

修改流程：

更新MySQL
↓
删除Redis

删除流程：

删除MySQL
↓
删除Redis

新增流程：

只新增MySQL

（详情缓存原本不存在，不需要删除）


---

二、项目代码完成

修改了：

updateKnowledge()

数据库修改成功以后：

String key = "knowledge:" + id;
redisTemplate.delete(key);

实现：

> 更新数据库 → 删除缓存




---

deleteKnowledge()

数据库删除成功以后：

String key = "knowledge:" + id;
redisTemplate.delete(key);

实现：

> 删除数据库 → 删除缓存




---

addKnowledge()

无需修改。

因为新增的数据以前没有详情缓存。


---

三、今天新增知识点

① Cache Aside

最经典的缓存模式。

流程：

读：

Redis

↓

MySQL

↓

Redis


写：

MySQL

↓

删除Redis


---

② 为什么更新数据库以后删除缓存

原因：

Redis只是缓存。

MySQL才是真正的数据源。

删除缓存：

下次查询自动重新建立缓存。


---

③ 为什么不更新Redis

因为：

更新缓存失败概率更高。

删除缓存：

更加简单。

更加可靠。


---

④ 为什么不能先删除缓存

因为：

删除Redis

↓

用户查询

↓

MySQL还是旧数据

↓

旧数据重新进入Redis

↓

数据库更新

↓

Redis变成旧数据

导致缓存和数据库不一致。


---

⑤ 更新数据库再删除缓存也不是绝对一致

仍然存在：

窗口期。

例如：

更新MySQL

↓

Redis还没删除

↓

用户读到旧缓存

↓

删除Redis

用户可能短时间读到旧数据。

所以：

Cache Aside保证的是：

> 最终一致性。



不是：

> 强一致性。




---

⑥ 延迟双删（了解）

流程：

更新MySQL

↓

删除Redis

↓

等待几十毫秒

↓

再次删除Redis

作用：

进一步降低窗口期产生旧缓存的概率。

属于：

项目第二阶段优化。

目前了解即可。


---

四、今天踩坑

今天没有新的Bug。

Code Review发现一个优化：

KnowledgeEntity entity = knowledgeMapper.selectById(id);

重复查询数据库。

应删除。

避免无意义SQL。


---
---

Day19 总结 ✅

学了什么

1. Cache Aside缓存模式

查缓存

缓存没有查数据库

查到后写入缓存



2. 缓存穿透

查询不存在数据导致大量请求访问数据库

解决：缓存空值、布隆过滤器



3. 缓存雪崩

大量缓存同时过期导致数据库压力过大

解决：随机过期时间



4. Redis序列化

JSON没有Java类型信息会导致反序列化成LinkedHashMap

需要保存类型信息才能恢复VO对象





---

做了什么

1. 完善 getKnowledgeById Redis缓存：



Redis命中直接返回

未命中查询MySQL

查询成功写入Redis


2. 增加缓存穿透处理：



不存在数据 → Redis保存"NULL"

避免重复查询数据库。

3. 增加随机过期时间：



31 + random.nextInt(5)

避免缓存同时失效。

4. 修复Redis序列化Bug：



问题：

KnowledgeDetailVO
↓
Redis
↓
LinkedHashMap

解决：

开启 Jackson 类型信息，使 Redis 可以恢复：

Redis
↓
KnowledgeDetailVO

5. 完成验证：



第一次：

Redis无数据
→ MySQL
→ 写Redis

第二次：

Redis命中
→ 直接返回KnowledgeDetailVO


---