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