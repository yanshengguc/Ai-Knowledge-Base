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