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
Day20 快照

【学习内容】

1. Redis缓存基础

学习 Spring Boot 整合 Redis

理解 Cache Aside（旁路缓存）模式：


查询数据
 ↓
查Redis
 ↓
命中返回
 ↓
未命中查MySQL
 ↓
写入Redis


---

2. 缓存三大问题

✅ 缓存穿透

问题：

请求不存在数据
 ↓
Redis没有
 ↓
一直查MySQL

解决：

缓存空值 NULL

设置过期时间



---

✅ 缓存雪崩

问题：

大量key同时过期
 ↓
大量请求打MySQL

解决：

TTL随机化



---

✅ 缓存一致性

理解：

更新数据：

修改MySQL
 ↓
删除Redis缓存

避免读取旧数据。


---

3. Redis序列化

学习：

Redis存储的是JSON

Java对象需要序列化/反序列化

GenericJackson2JsonRedisSerializer解决对象类型恢复问题



---

【写的代码】

1. Redis配置

新增：

RedisConfig

实现：

RedisTemplate配置

Key使用String序列化

Value使用JSON序列化

支持LocalDateTime


核心：

objectMapper.registerModule(
    new JavaTimeModule()
);


---

2. 知识详情缓存

修改：

KnowledgeServiceImpl.getKnowledgeById()

实现：

Redis查询

redisTemplate.opsForValue().get(key)

缓存命中

return (KnowledgeDetailVO)obj;

未命中查询MySQL

Redis
 ↓
MySQL
 ↓
写Redis


---

3. 缓存穿透处理

新增：

不存在数据缓存：

redisTemplate.opsForValue()
.set(key,"NULL",5,TimeUnit.MINUTES);

读取：

if("NULL".equals(obj)){
    throw new BusinessException("不存在");
}


---

4. 随机TTL防雪崩

新增：

private final Random random = new Random();

缓存时间：

31 + random.nextInt(5)


---

5. 修改缓存一致性

修改接口：

updateKnowledge()

新增：

redisTemplate.delete(key);

流程：

更新MySQL
 ↓
删除Redis


---

6. 删除缓存一致性

删除接口：

deleteKnowledge()

新增：

redisTemplate.delete(key);

流程：

删除MySQL
 ↓
删除Redis


---

【Bug记录】

1. LinkedHashMap转换异常

错误：

LinkedHashMap cannot cast KnowledgeDetailVO

原因：

Redis反序列化不知道对象类型。

解决：

使用：

GenericJackson2JsonRedisSerializer

并开启类型信息。


---

2. LocalDateTime序列化失败

原因：

Jackson默认不支持LocalDateTime。

解决：

加入：

jackson-datatype-jsr310

并注册：

JavaTimeModule


---

3. 测试误判

JWT一直打印：

eyJhbGciOiJIUzI1...

原因：

Jwt过滤器打印请求头。

不是Redis返回。


---
Day21 总结：Redis缓存问题治理（缓存穿透 / 雪崩 / 击穿）

一、今天学习内容

1. 缓存穿透

问题：

大量请求查询不存在的数据：

请求不存在id
      ↓
Redis没有
      ↓
查询MySQL
      ↓
MySQL也没有

如果攻击大量不存在的id：

大量请求 → MySQL压力巨大


---

解决方案：空值缓存

实现：

redisTemplate.opsForValue()
.set(key,"NULL",5,TimeUnit.MINUTES);

流程：

第一次请求
 ↓
查MySQL
 ↓
不存在
 ↓
Redis保存NULL


之后请求
 ↓
Redis发现NULL
 ↓
直接返回不存在


---

2. 缓存雪崩

问题：

大量缓存同时过期：

100万个key
30分钟同时过期

        ↓

大量请求进入MySQL


---

解决方案：随机过期时间

代码：

31 + random.nextInt(5)

实际：

31~35分钟随机过期

效果：

key1 31分钟过期
key2 33分钟过期
key3 35分钟过期

避免同一时间大量失效。


---

3. 缓存击穿（重点）

问题：

热点数据突然过期：

热门文章缓存失效

       ↓

大量请求同时查询

       ↓

全部访问MySQL


---

解决方案：Redis分布式锁

核心：

setIfAbsent()

作用：

只有一个线程可以查询数据库。

流程：

请求1
 ↓
Redis无缓存
 ↓
获取锁成功
 ↓
查MySQL
 ↓
写Redis
 ↓
释放锁


请求2~N
 ↓
获取锁失败
 ↓
等待
 ↓
重新查Redis
 ↓
返回缓存


---

二、今天写的代码

1. Redis缓存查询优化

原：

查询MySQL
返回

改：

查询Redis

有：
返回缓存

无：
查询MySQL
写入Redis


---

2. 空值缓存

新增：

if(entity == null){

    redisTemplate.opsForValue()
    .set(key,"NULL",5,TimeUnit.MINUTES);

}


---

3. 随机过期时间

新增：

private final Random random = new Random();

缓存：

redisTemplate.opsForValue()
.set(
 key,
 VO,
 31+random.nextInt(5),
 TimeUnit.MINUTES
);


---

4. Redis分布式锁

新增：

String lockKey = "lock:" + key;

String lockValue =
UUID.randomUUID().toString();

获取锁：

redisTemplate.opsForValue()
.setIfAbsent(
 lockKey,
 lockValue,
 10,
 TimeUnit.SECONDS
);


---

5. 双重检查缓存

为什么？

防止：

线程A拿锁查询数据库

线程B等待

A写入Redis释放锁

B拿锁后再次查缓存

所以锁里面再次：

obj = redisTemplate.opsForValue()
.get(key);


---

三、今天遇到的问题

1. InterruptedException

原因：

测试锁等待加入：

Thread.sleep(100);

导致：

InterruptedException


---

临时解决：

Controller：

throws InterruptedException

可以启动。


---

最终正确方案：

Service处理：

try{
    Thread.sleep(100);
}catch(InterruptedException e){

    Thread.currentThread().interrupt();

}

原因：

Controller不应该处理业务内部异常。


---

2. Controller启动异常

问题：

修改Controller方法名后恢复。

原因：

可能存在：

方法重复

Spring MVC映射冲突


记录：

Controller看的是：

请求方式 + 路径

不是方法名。


---

四、测试结果

已验证：

Redis缓存命中

第一次：

Redis查询:null
走MYSQL

第二次：

Redis查询:KnowledgeDetailVO
走缓存

✅成功


---

修改缓存同步

流程：

修改数据
 ↓
MySQL update
 ↓
删除Redis
 ↓
再次查询
 ↓
重新加载缓存

测试成功。


---

删除缓存同步

流程：

删除数据
 ↓
MySQL delete
 ↓
删除Redis
 ↓
再次查询
 ↓
返回不存在

测试成功。


---

五、还未优化（后续补）

1. Lua安全释放锁

目前：

redisTemplate.delete(lockKey);

问题：

可能误删其他线程锁。

后续：

判断lockValue
      ↓
属于自己
      ↓
删除


---

2. 重试机制优化

目前：

return getKnowledgeById(id);

递归等待。

后续：

改：

while循环
+
最大重试次数


---

3. Redisson

后面会学习。

替代：

手写Redis分布式锁

提供：

自动续期

可重入锁

Lua释放

更完善的锁管理



---
Day22 总结：文件上传接口 + 本地存储（AI智能图书馆「电子档案室」）

一、今天解决了什么问题？

今天给 AI 智能图书馆增加了一个能力：

> 用户可以上传资料，后端接收文件，并保存到服务器指定位置。



相当于：

以前：

用户
 ↓
输入知识内容
 ↓
MySQL保存

现在：

用户
 ↓
上传PDF/图片/文档
 ↓
服务器保存文件
 ↓
后续解析文件内容
 ↓
进入RAG流程

今天搭建的是 AI 知识库的「资料入口」。


---

二、核心流程（图书馆模型）

用户
 ↓
JWT访客证
 ↓
FileController（前台）
 ↓
FileService（管理员）
 ↓
FileServiceImpl（具体工作）
 ↓
生成唯一文件名
 ↓
D:/upload（电子档案室）

对应：

模块	图书馆角色	作用

MultipartFile	快递包裹	接收用户上传的文件
Controller	前台	接收请求
Service	管理员	负责业务逻辑
UUID	编号系统	防止文件重名
本地目录	电子档案室	保存文件



---

三、今天完成代码

1. FileService接口

作用：

定义上传能力。

String uploadFile(MultipartFile file);

为什么需要接口？

因为：

Controller 不应该依赖具体实现。

结构：

Controller
    ↓
FileService接口
    ↓
FileServiceImpl

方便以后替换：

本地存储
      ↓
阿里云OSS

Controller 不需要改。


---

2. FileServiceImpl实现上传

核心代码：

String originalName = file.getOriginalFilename();

String uuid = UUID.randomUUID().toString();

String newName = uuid + "_" + originalName;

作用：

解决文件重名问题。

例如：

两个用户上传：

Java学习.pdf

如果直接保存：

Java学习.pdf

第二个会覆盖第一个。

现在：

uuid_Java学习.pdf

每个文件唯一。


---

创建目录

File dir = new File(uploadPath);

if(!dir.exists()){
    dir.mkdirs();
}

作用：

如果上传目录不存在：

自动创建。


---

保存文件

file.transferTo(new File(dir,newName));

作用：

把 MultipartFile 写入磁盘。


---

四、异常处理

遇到：

file.transferTo()

需要处理：

IOException

原因：

文件保存可能失败：

路径不存在

权限不足

磁盘错误


处理：

try {

} catch(IOException e){

    throw new BusinessException("文件上传失败");

}

流程：

IOException
 ↓
Service捕获
 ↓
转换业务异常
 ↓
GlobalExceptionHandler统一处理

符合项目已有异常体系。


---

五、FileController上传接口

代码逻辑：

@PostMapping("/upload")
public Result<String> upload(MultipartFile file){

    String url=fileService.uploadFile(file);

    return Result.success(url);
}

作用：

提供HTTP入口。

请求：

POST /api/file/upload

参数：

file


---

六、测试过程

使用 Apifox：

请求：

POST
/api/file/upload

Headers:

Authorization: Bearer token

Body：

form-data

file → 选择文件

测试结果：

✅ 返回成功

✅ D:/upload目录生成文件

说明：

完整链路成功。


---

七、静态资源访问问题

发现：

上传成功：

D:/upload/test.jpg

但是浏览器访问：

/files/test.jpg

失败。

原因：

1. 路径写错

原：

/file/**

访问：

/files/**

修改：

registry.addResourceHandler("/files/**")
        .addResourceLocations("file:D:/upload/");


---

2. JWT拦截问题

理解：

Filter会拦截所有请求：

浏览器访问图片

↓

JwtAuthenticationFilter

↓

没有Token

↓

401


---

今天讨论了一个重要设计：

公开资源：

例如：

logo
首页图片

可以：

/images/**

放白名单。

私有资源：

例如：

用户上传PDF
学习资料

不应该直接公开。

以后应该：

GET /api/file/download/{id}

↓

JWT

↓

权限检查

↓

返回文件


---

八、今天踩坑记录（Bug日志）

Bug1：Service接口忘记定义

现象：

调用 FileService 报错。

原因：

Controller依赖接口，但是没有创建接口。

解决：

创建：

FileService

经验：

三层架构：

Controller
 ↓
Service接口
 ↓
ServiceImpl


---

Bug2：transferTo要求异常

现象：

IDE要求：

throws IOException

原因：

文件操作属于可能失败的IO操作。

解决：

Service内部捕获，转换：

BusinessException


---

Bug3：静态资源访问401

现象：

Apifox上传成功，但是浏览器访问图片失败。

原因：

上传请求带JWT，浏览器直接访问没有Token。

经验：

JWT Filter影响的不只是Controller，也会影响资源请求。


---
Day23: integrate Aliyun OSS for file upload

完成内容：
1. 接入阿里云 OSS 对象存储
- 创建 OSS Bucket
- 配置 Endpoint
- 创建 RAM 用户并获取 AccessKey
- Spring Boot 集成 aliyun-sdk-oss

2. 完成 OSS 配置
- 创建 OssConfig 配置类
- 使用 @Value 读取 OSS 配置
- 通过 OSSClientBuilder 创建 OSSClient Bean
- 交由 Spring 容器管理

3. 完成文件上传重构
原流程：
MultipartFile
    ↓
file.transferTo()
    ↓
本地磁盘存储

新流程：
MultipartFile
    ↓
FileService
    ↓
OssService
    ↓
ossClient.putObject()
    ↓
阿里云 OSS
    ↓
返回文件访问 URL

4. 完善分层设计
新增：
service
 └── OssService.java

service.impl
 └── OssServiceImpl.java

职责：
- FileService：处理文件业务流程
- OssService：负责具体 OSS 存储实现

5. 文件上传逻辑
- 获取原文件名
- UUID 重命名，避免文件覆盖
- 调用 OSS putObject 上传
- 拼接并返回文件访问地址

6. 测试验证
- 登录获取 JWT Token
- 携带 Token 调用上传接口
- Apifox 测试上传成功
- OSS 控制台确认文件存在


项目模型整理：

新增：
云端档案室（OSS）

完整链路：

用户
 ↓
JWT认证
 ↓
FileController
 ↓
FileService
 ↓
OssService
 ↓
阿里云 OSS
 ↓
返回文件 URL


今日知识点：
- OSS 是什么
- OSS vs 本地存储
- 为什么文件不存 MySQL
- AccessKey 安全管理
- Service 分层设计
- MultipartFile 上传流程
Day24 总结：文件信息入库 + Knowledge 关联（完成）

一、今日目标

实现：

OSS 上传文件
        ↓
获得文件 URL
        ↓
保存文件元信息到 MySQL
        ↓
文件关联 Knowledge
        ↓
绑定当前用户权限

最终让 AI 图书馆拥有：

> 「知识 → 文件」的关联关系。




---

二、今天新增模块

1. FileEntity 文件实体

负责保存文件信息：

private Long id;
private Long userId;
private String fileName;
private String fileType;
private Long fileSize;
private String fileUrl;
private Long knowledgeId;
private LocalDateTime createTime;
private LocalDateTime updateTime;

对应数据库：

knowledge_file


---

三、数据库设计

新增文件表：

CREATE TABLE knowledge_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(50),
    file_size BIGINT,
    file_url VARCHAR(500),
    knowledge_id BIGINT NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    FOREIGN KEY (knowledge_id) REFERENCES knowledge(id)
);

关系：

user
 |
 | 1
 |
 N
knowledge
 |
 | 1
 |
 N
knowledge_file

含义：

一个用户：

可以创建多个知识


一个知识：

可以上传多个文件



---

四、重要设计理解

1. knowledge.id 和 user_id 区别

之前容易混淆：

knowledge.id

表示：

> 这条知识自己的编号



例如：

knowledge

id=12
title=Redis


---

user_id

表示：

> 谁创建了这条知识



例如：

user

id=1
username=yan


knowledge

id=12
user_id=1

表示：

用户 yan 创建了知识 Redis。


---

五、上传流程

现在完整链路：

用户
 ↓
JWT
 ↓
Filter
 ↓
UserContext 获取 userId
 ↓
Controller
 ↓
FileService
 ↓
查询 Knowledge
 ↓
判断是否属于当前用户
 ↓
OssService
 ↓
OSS上传
 ↓
返回URL
 ↓
保存knowledge_file


---

六、权限设计

上传文件不能只靠前端传：

knowledgeId

因为用户可能恶意修改。

正确：

JWT
 ↓
UserContext
 ↓
得到当前用户id

然后：

KnowledgeEntity knowledge =
        knowledgeMapper.selectById(knowledgeId);


if(!userId.equals(knowledge.getUserId())){
    throw new BusinessException("无权上传");
}

保证：

> 只有知识拥有者才能上传文件。




---

七、今天遇到的问题

问题1：knowledgeId 为 null

错误：

Parameters: null

原因：

Controller：

Long knowledgeId

没有绑定路径参数。

解决：

@PathVariable Long knowledgeId

并修改：

@PostMapping("/upload/{knowledgeId}")


---

问题2：404

原因：

请求路径错误。

Controller：

@RequestMapping("/api/file")
@PostMapping("/upload/{knowledgeId}")

完整路径：

/api/file/upload/12


---

问题3：数据库不存在 knowledge_file

错误：

Table 'knowledge_file' doesn't exist

原因：

MyBatis：

insert into knowledge_file

但是数据库没有创建。

解决：

创建：

knowledge_file


---

问题4：返回 id=null

现象：

{
"id":null
}

原因：

MyBatis 没有回填自增主键。

优化：

<insert
 useGeneratedKeys="true"
 keyProperty="id">

让数据库生成的 id 自动回到 Entity。


---
Day25 总结：文档解析

一、完成内容

✅ 设计文档解析接口

DocumentParser
        |
 ┌──────┴──────┐
PdfParser   WordParser

统一：

String parse(MultipartFile file);

返回解析后的文本。


---

二、实现功能

✅ PDF解析

流程：

MultipartFile
 ↓
InputStream
 ↓
PDFBox
 ↓
String文本

核心：

file.getInputStream()

Loader.loadPDF()

PDFTextStripper



---

✅ Word解析

流程：

MultipartFile
 ↓
InputStream
 ↓
Apache POI
 ↓
String文本

核心：

XWPFDocument

getParagraphs()

StringBuilder拼接



---

三、设计思想

使用：

接口 + 实现类 + 工厂

原因：

不让 Service 负责判断文件类型

符合开闭原则

新增格式只增加 Parser


例如：

MarkdownParser
ExcelParser

不用修改已有代码。


---

四、重要 Bug

Bug1：PDF解析失败

错误：

Loader.loadPDF(file.getOriginalFilename())

原因：

传入的是文件名，不是文件内容。

解决：

file.getInputStream()


---

五、RAG链路位置

完成：

文件上传
 ↓
OSS
 ↓
文档解析
 ↓
String文本

下一步：

String
 ↓
Chunk切片
 ↓
Embedding
 ↓
向量库


---

Day25 状态

✅ PDF解析
✅ Word解析
✅ Parser接口设计
✅ 为RAG切片准备数据
Day26 总结：文档切片（Chunk）重点版

一、完成内容

✅ 实现文本切片器 SimpleTextSplitter

流程：

String文本
    ↓
切片
    ↓
List<String> chunks


---

二、核心设计

接口：

public interface DocumentSplitter {

    List<String> split(String text);

}

实现：

DocumentSplitter
        |
SimpleTextSplitter


---

三、切片核心逻辑

核心参数：

chunkSize = 每个片段长度

overlap = 重叠长度

step = chunkSize - overlap

例如：

chunkSize = 500
overlap = 100

step = 400

每次移动 400，保留 100 个字符上下文。


---

四、核心代码思想

流程：

start开始位置

↓

计算end

↓

substring截取

↓

保存chunk

↓

start向前移动

核心：

int end = Math.min(start + chunkSize, text.length());

String chunk = text.substring(start,end);

chunks.add(chunk);

start += chunkSize - overlap;


---

五、重要 Bug

Bug1：尾部重复切片

测试：

输入：

ABCDEFGHIJK

输出：

ABCDE
DEFGH
GHIJK
JK

原因：

最后一个 chunk 已经包含结尾内容，但循环继续执行。

解决：

if(end == text.length()){
    break;
}


---

六、工程设计

加入参数校验：

防止：

overlap >= chunkSize

导致：

step <= 0

循环异常。


---

七、AI智能图书馆类比

Parser：

📖 阅读员

负责：

文件 → 文本

Splitter：

✂️ 切书员

负责：

文本 → 知识卡片

后续：

知识卡片 → 向量 → 检索


---

八、RAG链路进度

目前：

文件上传
 ↓
OSS
 ↓
PDF/Word解析
 ↓
String文本
 ↓
Chunk切片

下一步：

Chunk
 ↓
数据库保存
 ↓
Embedding
 ↓
向量库


---

Day26 状态

✅ 完成切片器设计
✅ 掌握 chunk_size / overlap / step
✅ 完成滑动窗口迁移
✅ 发现并修复边界问题
Day27 总结（2026-08-04）

项目完成 ✅

1. Chunk 数据库存储

完成 knowledge_chunk 表设计：

knowledge_file
       |
       | 1:N
       ↓
knowledge_chunk

字段：

id

fileId

chunkIndex

content

contentLength

createTime

updateTime


原因：

一个文件会被切成多个 Chunk

Chunk 后续需要单独做 Embedding 和向量检索

不能直接把所有内容存一个字段，否则失去切片意义



---

2. Chunk 批量入库 ✅

完成：

List<String>
      ↓
ChunkEntity
      ↓
MyBatis foreach
      ↓
knowledge_chunk

实现：

设置 fileId

设置 chunkIndex

保存切片内容

计算内容长度

批量插入


使用：

@Transactional

保证批量保存失败可以回滚。


---

3. 文档处理流程打通 ✅

完成：

FileService
      ↓
DocumentService
      ↓
ParserFactory
      ↓
DocumentParser
      ↓
DocumentSplitter
      ↓
ChunkService

完整 RAG 预处理链路：

上传文件
 ↓
OSS
 ↓
knowledge_file
 ↓
PDF/Word解析
 ↓
文本切片
 ↓
knowledge_chunk


---

测试结果 ✅

PDF 测试

成功：

PDF 上传 ✅

PDFBox 解析 ✅

切片生成 ✅

Chunk 入库 ✅


数据库验证：

file_id = 2

chunk_index:
0
1
2


---

Word 测试

第一次失败：

错误：

Data too long for column 'file_type'

原因：

Word MIME 类型过长：

application/vnd.openxmlformats-officedocument.wordprocessingml.document

数据库字段长度不足。

解决：

ALTER TABLE knowledge_file
MODIFY file_type VARCHAR(255);

修改后：

Word 上传成功 ✅


---

今日 Bug 记录

1. Spring Bean 未注册

错误：

No qualifying bean of type PdfParser

原因：

实现类没有加入 IOC。

解决：

@Component

添加到：

PdfParser

WordParser

SimpleTextSplitter



---

2. 变量名错误

错误：

chunksService.saveChunks()

实际：

chunkService.saveChunks()


---

3. 空文档异常

问题：

空文档解析失败。

优化：

在解析后、切片前增加：

if (text == null || text.isBlank()) {
    throw new BusinessException("文档内容为空");
}

避免无效 Chunk 入库。


---

4. Apifox 响应校验

现象：

异常测试提示：

Expected 200
Actual 500

原因：

Apifox 默认校验成功场景。

解决：

异常接口单独设置：

正常上传 → 200

空文档/解析失败 → 500（或后续优化为400）



---

今日核心理解

RAG 第一阶段已经完成：

文档
 ↓
解析 Parser
 ↓
文本 String
 ↓
切片 Splitter
 ↓
Chunk
 ↓
数据库

下一阶段：

Chunk
 ↓
Embedding
 ↓
向量库
 ↓
相似度检索
 ↓
LLM回答




---


当前进度：

Day25 文档解析 ✅
Day26 文档切片 ✅
Day27 Chunk 入库 + 联调 ✅

RAG 数据预处理阶段完成。
---

Day28 总结（2026-08-05）

今日目标

优化：

> 上传文件 → 解析 → 切片 → Chunk入库



完整链路的可维护性。


---

一、完成内容

1. 增加业务日志 ✅

新增：

FileServiceImpl

记录：

开始上传文件

权限校验成功

OSS上传成功

文件信息保存成功

文档处理完成


DocumentServiceImpl

记录：

开始处理文档

文档解析完成

文档切片完成

Chunk保存完成


形成完整链路：

上传开始
 ↓
OSS成功
 ↓
knowledge_file保存成功
 ↓
开始解析
 ↓
解析完成
 ↓
切片完成
 ↓
Chunk入库完成


---

二、日志设计原则（面试）

不要：

log.info("text={}", text);

原因：

1. 文档内容可能巨大，导致日志膨胀


2. 可能泄露用户敏感文件内容


3. 不方便日志检索



应该记录：

fileId
userId
文件名
文本长度
Chunk数量
状态

例如：

log.info(
"文档解析完成,fileId={},textLength={}",
fileId,
text.length()
);


---

三、测试结果 ✅

测试文件：

PDF

结果：

文件保存

成功：

fileId=7
knowledgeId=12


---

文档解析

成功：

textLength=1153


---

文档切片

成功：

chunkCount=3

切片策略：

chunkSize=500
overlap=100


---

Chunk批量入库

SQL：

INSERT INTO knowledge_chunk
VALUES
(...),
(...),
(...)

说明：

不是循环 insert。

使用：

MyBatis foreach批量插入


---

事务

日志：

Transaction synchronization committing SqlSession

说明：

@Transactional

生效。


---

四、今日面试知识点

1. overlap 为什么存在？

错误：

> 为了减少检索时间



修正：

> overlap 用于保留相邻 Chunk 的上下文，避免语义被切断，提高向量检索召回效果。




---

2. 为什么记录 contentLength 而不是 length？

因为：

字符数量 ≠ 存储大小

例如：

英文:
abc

中文:
你好

UTF-8：

英文：

3 bytes

中文：

6 bytes

所以：

chunk.getBytes(StandardCharsets.UTF_8).length

更符合存储统计。


---

3. OSS 和数据库事务问题

问题：

OSS成功

↓

数据库失败

无法靠 MySQL 回滚 OSS。

原因：

OSS 是外部系统，不参与数据库事务。

解决：

补偿删除

定时清理

状态管理



---

五、发现的小问题

MyBatis SQL日志打印了 Chunk 内容：

原因：

开发环境开启：

StdOutImpl

生产环境需要关闭。

否则：

文档泄露

日志过大



---

六、项目模型整理（15分钟）

今天 AI 智能图书馆新增：

文档处理流水线监控系统

新增能力：

上传文件
 ↓
OSS保存
 ↓
文件记录
 ↓
解析
 ↓
切片
 ↓
Chunk库存储

今天连接：

Day22 文件上传
       +
Day25 文档解析
       +
Day26 切片
       +
Day27 Chunk入库
       +
Day28 日志优化

完整形成：

> RAG 数据预处理阶段。


Day28 完成 ✅
Day29 总结（2026-08-05）

阶段：

> OSS + 文档处理优化阶段



目标：

> 完善文件处理链路的工程化能力，让上传、解析、切片失败时可追踪、可恢复。




---

今日完成内容

1. 文件上传参数校验 ✅

在 FileServiceImpl.uploadFile() 增加：

空文件校验

if (file.isEmpty()) {
    throw new BusinessException("文件不能为空");
}

作用：

避免：

空文件上传 OSS

无效解析

生成空 Chunk



---

文件大小限制

long maxSize = 20 * 1024 * 1024;

if (file.getSize() > maxSize) {
    throw new BusinessException("文件大小不能超过20MB");
}

作用：

防止：

大文件占用过多内存

PDF/Word解析失败

OOM风险


面试回答：

> 文件大小限制应该放在业务入口处提前校验，避免无效文件进入后续 OSS 上传和解析流程。




---

2. 文件处理状态管理（重点）⭐⭐⭐⭐⭐

新增：

knowledge_file.status

字段：

status VARCHAR(20)

状态：

PROCESSING
    |
    |
 SUCCESS

 FAILED


---

为什么增加状态？

之前：

上传
 ↓
解析
 ↓
结束

问题：

如果解析失败：

数据库不知道当前状态。

现在：

上传成功
 ↓
PROCESSING
 ↓
解析切片
 ↓
SUCCESS / FAILED

用户可以知道文件当前状态。


---

3. 增加 updateStatus 方法

Mapper：

int updateStatus(
    @Param("id") Long id,
    @Param("status") String status
);

XML：

<update id="updateStatus">
    UPDATE knowledge_file
    SET
        status = #{status},
        update_time = NOW()
    WHERE id = #{id}
</update>


---

4. 异常状态处理优化

修改：

documentService.handleDocument(file, entity.getId());

增加：

try-catch

流程：

成功：

PROCESSING
      ↓
解析成功
      ↓
SUCCESS

失败：

PROCESSING
      ↓
解析异常
      ↓
FAILED
      ↓
抛异常
      ↓
GlobalExceptionHandler


---

5. GlobalExceptionHandler完善

增加：

@ExceptionHandler(Exception.class)

处理：

系统异常

未预期异常


业务异常：

@ExceptionHandler(BusinessException.class)

统一返回：

{
 "code":500,
 "message":"文件不能为空"
}


---

6. 测试结果 ✅

空文件测试

结果：

{
 "code":500,
 "message":"文件不能为空"
}

说明：

业务异常链路正常。


---

空PDF测试

结果：

PDFBox解析失败：

Error: End-of-File

状态：

FAILED

说明：

异常捕获和状态更新正常。


---

PDF测试

成功：

textLength=1153
chunkCount=3
status=SUCCESS


---

Word测试

成功：

课程设计说明.docx

textLength=644
chunkCount=2
status=SUCCESS


---

今日Bug记录

Bug1：8080端口占用

原因：

旧SpringBoot进程未关闭。

解决：

关闭占用8080的进程。


---

Bug2：空PDF上传返回500

原因：

文件存在，但是内容不是合法PDF。

PDFBox解析失败。

解决：

异常统一交给：

GlobalExceptionHandler


---


Day29 完成。✅
# Day31 总结(2026-08-07)

## 一、项目进度

**目标**:接入Embedding模型,把切片转成向量,为RAG检索打基础。

**完成情况**:
- ✅ 选定阿里通义`text-embedding-v3`,开通百炼平台API Key
- ✅ 密钥安全隐患顺手修复:数据库密码/OSS密钥/API Key 从`application.properties`明文抽离到`application-local.properties`,并加入`.gitignore`(此前一直是明文,虽未推送远程但已是隐患)
- ✅ 引入`spring-ai-alibaba-starter-dashscope`依赖 + Spring milestone仓库配置
- ✅ 验证`EmbeddingModel`直接调用成功,**向量维度1024**(这个数字要记住,Day32建向量表要用)
- ✅ 封装`EmbeddingService`接口 + 实现类,加入参数校验(`StringUtils.hasText`,失败抛`IllegalArgumentException`)
- ✅ 5个单元测试全过(正常场景 + 空字符串/null/纯空格三种边界)

**过程中的自我修正(值得记住)**:一开始写完Service层直接说"完成",没有测试就下结论——被自己意识到并纠正,补上了针对Service层的测试。这个"没跑过不算数"的意识要保持。

---

## 二、八股新增(今天最大收获)

### ① 向量检索 vs 关键词检索的本质区别
关键词检索比的是"字面是否相同",向量检索比的是"语义是否相近"。例:"电脑打不开"和"设备无法开机"字面零重合,但语义相同,只有向量检索能匹配上。

### ② Redis为什么不适合做向量检索
- Redis的数据结构(String/Hash/List/Set/ZSet)不是为高维向量设计的
- ZSet虽能排序,但排的是一维分数,向量是几百/上千维,塞不进去
- 只能暴力比对,复杂度O(n),数据量大了延迟不可接受

### ③ 近似最近邻索引(ANN)原理
向量数据库(如Milvus)用HNSW/IVF这类索引,通过图结构/聚类跳过大部分不相关向量,把搜索复杂度从O(n)降到接近O(log n)。**代价是精度损失**——不保证100%找到数学上绝对最近的Top K,是"近似"最近邻,用可接受的精度损失换数量级的速度提升。

**今天的答题模式**:三道题都出现了"第一层答案就停"的老问题(比如一开始说"索引维度""减少不多但数据量大"这类模糊/不准确的表述),经过反复追问才补全完整链路,但复述后基本能掌握。这个模式还需要继续在后续复习中巩固。

---

## 三、图书馆世界观新增比喻

**Embedding Service = "AI馆长的翻译官"**
把切片(中文文本)翻译成向量("数字语言"),自己不存储任何东西,翻译完就交出去。真正的"仓库"是Day32要建的向量数据库。

链路:文档解析 → 切片(Chunk)→ **翻译成向量(今天新增)** → (明天存入向量库)

---

## 四、算法

今天没有安排新题,438(双数组固定窗口)继续留在明天早上默写巩固,不要碰差分数组写法。

---
# 今日总结(2026-08-07,Day32+33合并推进)

## 一、项目进度

**目标**:向量数据库选型+搭建+存储链路打通(手册Day33选型提前合并进Day32存储)

**完成情况**:
- ✅ 选型:阿里云**DashVector**(生态统一、全托管、Serverless、Cosine相似度)
- ✅ Cluster + Collection创建(dimension=1024,匹配Day31的text-embedding-v3)
- ✅ Schema设计:`document_id`(long)、`content`(string)
- ✅ 密钥外置:DashVector的api-key/endpoint同样走`application-local.properties`,延续Day31的安全习惯
- ✅ `VectorStoreService`接口+实现类,`@PostConstruct`初始化连接复用
- ✅ 端到端测试:文本→Embedding→存入DashVector,控制台确认真实落库

**接口设计的一个好决定**:一开始`chunkId`/`documentId`用了`String`,后来主动纠正为`Long`(跟MySQL实体类型保持一致),没有用运行时转换去兜底掩盖类型问题——这是今天体现出的架构判断力的加分项。

---

## 二、今天排查的三个真实Bug(比顺利跑通更有价值,记进错题库)

1. **Endpoint复制不全**:只复制了实例ID前半段,少了`.dashvector.cn-shenzhen.aliyuncs.com`这一截,报`Invalid endpoint`
2. **Maven依赖冲突**:TestNG间接引入guava 19.0旧版本,和DashVector SDK底层gRPC所需的guava新方法冲突,报`NoSuchMethodError`。解决:`pom.xml`里显式声明guava 32.1.3-jre高版本覆盖
3. **字段类型不匹配**:Schema定义`document_id`为long,插入时传了String,报"field value must be long"——顺带触发了刚才那个接口类型设计的修正

---

## 三、八股(选型题,已练熟)

**DashVector vs Milvus的权衡**:
- 优势:生态统一(复用OSS/DashScope账号体系)、全托管省运维、适合冲刺阶段快速开发
- 代价:数据主权/合规风险(第三方托管)、索引算法参数不可控(被框架锁定)、持续云服务成本、大规模生产环境企业更倾向自建开源方案获得更高可控性

**顺带巩固了Day31的知识**:余弦相似度作为默认度量方式,和向量语义检索的场景直接对应上了。

---

## 四、今天的答题模式

延续了之前的规律——第一次接触新概念/新报错时容易第一层作答就停(比如最初"选DashVector"只说了优势没提代价),经过追问后能补全完整链路,而且已经开始出现主动纠正的情况(接口类型设计那次)。这是进步的信号,继续在后续复习里巩固。

---

## 五、算法与Prompt

- 算法(438双数组)按计划挪到下午做
- Prompt Engineering进度在第四章节,慢慢推进,不强求对齐当天章节

---

已经把Day32+33的完整产出、三个Bug、选型权衡都更新进长期记忆。development-log里的Git提交、面经、Prompt总结栏目你自己补完即可。今天这两天量合并起来完成得很扎实,尤其是主动发现类型设计问题这一点,继续保持。