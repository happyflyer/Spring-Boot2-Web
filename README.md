# [Spring-Boot2-Web](https://github.com/happyflyer/Spring-Boot2-Web)

- [SpringBoot2 零基础入门](https://www.bilibili.com/video/BV19K4y1L7MT)
- [SpringBoot2 核心技术与响应式编程](https://www.yuque.com/atguigu/springboot)

## 1. SpringMVC 自动配置概览

- 简单功能分析
- 请求参数处理
- 数据响应与内容协商
- 视图解析与模板引擎
- 拦截器
- 跨域
- 异常处理
- 原生 Servlet 组件
- 嵌入式 Web 容器
- 定制化原理

## 2. 简单功能分析

### 2.1. 静态资源规则和定制化

- 静态资源目录
  - `/static`
  - `/public`
  - `/resources`
  - `/META-INF/resources`
- 静态映射 `/**`
- 请求进来
  - 先去找 Controller 看能不能处理
  - 不能处理的所有请求又都交给静态资源处理器
  - 静态资源也找不到则响应 404 页面

```yaml
spring:
  mvc:
    static-path-pattern: /res/**
  resources:
    static-locations: [classpath:/haha/]
```

- webjars
  - 自动映射 `/webjars/**`
  - [https://www.webjars.org/](https://www.webjars.org/)
  - [http://localhost:8080/webjars/jquery/3.5.1/jquery.js](http://localhost:8080/webjars/jquery/3.5.1/jquery.js)

```xml
<dependency>
  <groupId>org.webjars</groupId>
  <artifactId>jquery</artifactId>
  <version>3.5.1</version>
</dependency>
```

- 欢迎页
  - 静态资源路径下 `index.html`
  - controller 能处理 `/`
- favicon

```yaml
#spring:
#  mvc:
#    static-path-pattern: /res/**
```

```java
@RestController
public class HelloController {
    @RequestMapping("/")
    public String index() {
        return "index";
    }
}
```

### 2.2. 静态资源配置原理

#### 2.2.1. 自动配置类

- 配置文件的相关属性和 spring.xxx 进行了绑定
- `WebMvcProperties` == `spring.mvc`
- `ResourceProperties` == `spring.resources`

```java
package org.springframework.boot.autoconfigure.web.servlet;
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({Servlet.class, DispatcherServlet.class, WebMvcConfigurer.class})
@ConditionalOnMissingBean({WebMvcConfigurationSupport.class})
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
@AutoConfigureAfter({
        DispatcherServletAutoConfiguration.class,
        TaskExecutionAutoConfiguration.class,
        ValidationAutoConfiguration.class
})
public class WebMvcAutoConfiguration {
    // ...
    @Configuration(proxyBeanMethods = false)
    @Import(EnableWebMvcConfiguration.class)
    @EnableConfigurationProperties({ WebMvcProperties.class, ResourceProperties.class })
    @Order(0)
    public static class WebMvcAutoConfigurationAdapter implements WebMvcConfigurer {}
    // ...
}
```

```java
package org.springframework.web.servlet.config.annotation;
public interface WebMvcConfigurer {}
```

```java
package org.springframework.boot.autoconfigure.web.servlet;
@ConfigurationProperties(prefix = "spring.mvc")
public class WebMvcProperties {
}
```

```java
package org.springframework.boot.autoconfigure.web;
@ConfigurationProperties(prefix = "spring.resources", ignoreUnknownFields = false)
public class ResourceProperties {
}
```

- 配置类适配器只有一个有参构造器

```java
public WebMvcAutoConfigurationAdapter(
        ResourceProperties resourceProperties,
        WebMvcProperties mvcProperties,
        ListableBeanFactory beanFactory,
        ObjectProvider<HttpMessageConverters> messageConvertersProvider,
        ObjectProvider<ResourceHandlerRegistrationCustomizer> resourceHandlerRegistrationCustomizerProvider,
        ObjectProvider<DispatcherServletPath> dispatcherServletPath,
        ObjectProvider<ServletRegistrationBean<?>> servletRegistrations) {
    this.resourceProperties = resourceProperties;
    this.mvcProperties = mvcProperties;
    this.beanFactory = beanFactory;
    this.messageConvertersProvider = messageConvertersProvider;
    this.resourceHandlerRegistrationCustomizer = resourceHandlerRegistrationCustomizerProvider.getIfAvailable();
    this.dispatcherServletPath = dispatcherServletPath;
    this.servletRegistrations = servletRegistrations;
}
```

#### 2.2.2. 资源处理的默认规则

```java
// WebMvcAutoConfiguration.WebMvcAutoConfigurationAdapter
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (!this.resourceProperties.isAddMappings()) {
        logger.debug("Default resource handling disabled");
        return;
    }
    Duration cachePeriod = this.resourceProperties.getCache().getPeriod();
    CacheControl cacheControl = this.resourceProperties.getCache().getCachecontrol().toHttpCacheControl();
    if (!registry.hasMappingForPattern("/webjars/**")) {
        customizeResourceHandlerRegistration(registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCachePeriod(getSeconds(cachePeriod)).setCacheControl(cacheControl));
    }
    String staticPathPattern = this.mvcProperties.getStaticPathPattern();
    if (!registry.hasMappingForPattern(staticPathPattern)) {
        customizeResourceHandlerRegistration(registry.addResourceHandler(staticPathPattern)
                .addResourceLocations(getResourceLocations(this.resourceProperties.getStaticLocations()))
                .setCachePeriod(getSeconds(cachePeriod)).setCacheControl(cacheControl));
    }
}
```

- 禁用所有静态资源规则
- 设置缓存时间

```yaml
spring:
  resources:
    add-mappings: false
    cache:
      period: 11000
```

#### 2.2.3. 静态资源的默认路径

```java
package org.springframework.boot.autoconfigure.web;
@ConfigurationProperties(prefix = "spring.resources", ignoreUnknownFields = false)
public class ResourceProperties {
    private static final String[] CLASSPATH_RESOURCE_LOCATIONS = {
            "classpath:/META-INF/resources/",
            "classpath:/resources/",
            "classpath:/static/",
            "classpath:/public/"
    };
    private String[] staticLocations = CLASSPATH_RESOURCE_LOCATIONS;
    // ...
}
```

#### 2.2.4. 欢迎页处理器映射器

- `HandlerMapping`：处理器映射器。保存了每一个 Handler 能处理哪些请求

```java
// WebMvcAutoConfiguration.EnableWebMvcConfiguration
@Bean
public WelcomePageHandlerMapping welcomePageHandlerMapping(
        ApplicationContext applicationContext,
        FormattingConversionService mvcConversionService,
        ResourceUrlProvider mvcResourceUrlProvider) {
    WelcomePageHandlerMapping welcomePageHandlerMapping = new WelcomePageHandlerMapping(
            new TemplateAvailabilityProviders(applicationContext),
            applicationContext, getWelcomePage(),
            this.mvcProperties.getStaticPathPattern());
    welcomePageHandlerMapping.setInterceptors(getInterceptors(mvcConversionService, mvcResourceUrlProvider));
    welcomePageHandlerMapping.setCorsConfigurations(getCorsConfigurations());
    return welcomePageHandlerMapping;
}
```

```java
package org.springframework.boot.autoconfigure.web.servlet;
final class WelcomePageHandlerMapping extends AbstractUrlHandlerMapping {
    // ...
    WelcomePageHandlerMapping(
            TemplateAvailabilityProviders templateAvailabilityProviders,
            ApplicationContext applicationContext,
            Optional<Resource> welcomePage,
            String staticPathPattern) {
        if (welcomePage.isPresent() && "/**".equals(staticPathPattern)) {
            logger.info("Adding welcome page: " + welcomePage.get());
            setRootViewName("forward:index.html");
        }
        else if (welcomeTemplateExists(templateAvailabilityProviders, applicationContext)) {
            logger.info("Adding welcome page template: index");
            setRootViewName("index");
        }
    }
    // ...
}
```

## 3. 请求参数处理

### 3.1. REST 风格

- 以前
  - `/getUser` - 获取用户
  - `/saveUser` - 保存用户
  - `/editUser` - 修改用户
  - `/deleteUser` - 删除用户
- 现在，REST 风格
  - `/user`
    - `GET` - 获取用户
    - `POST` - 保存用户
    - `PUT` - 修改用户
    - `DELETE` - 删除用户
- 核心 Filter：`HiddenHttpMethodFilter`
- 用法
  - 表单 `method=post`
  - 隐藏域 `_method=put`
- SpringBoot 中手动开启

```java
@RestController
public class RequestController {
    @GetMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.GET)
    public String getUser() {
        return "GET-张三";
    }
    @PostMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.POST)
    public String saveUser() {
        return "POST-张三";
    }
    @PUTMDELETE ("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.PUT)
    public String putUser() {
        return "PUT-张三";
    }
    @DeleteMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.DELETE)
    public String deleteUser() {
        return "DELETE-张三";
    }
}
```

```html
<form action="/user" method="get">
  <input value="REST-GET 提交" type="submit" />
</form>
<form action="/user" method="post">
  <input value="REST-POST 提交" type="submit" />
</form>
<form action="/user" method="post">
  <input name="_method" type="hidden" value="put" />
  <input value="REST-PUT 提交" type="submit" />
</form>
<form action="/user" method="post">
  <input name="_method" type="hidden" value="delete" />
  <input value="REST-DELETE 提交" type="submit" />
</form>
```

```yaml
spring:
  mvc:
    hiddenmethod:
      filter:
        enabled: true
```

- 表单 REST
  - 表单提交会带上 `_method=PUT`
  - 请求过来被 `HiddenHttpMethodFilter` 拦截
  - 请求是否正常，并且是 POST
  - 获取到 `_method` 的值
  - 兼容以下请求
    - `PUT`
    - `DELETE`
    - `PATCH`
  - 原生 request（post），包装模式 `RequestWrapper` 重写了 `getMethod` 方法，返回的是传入的值
  - 过滤器链放行的时候用 wrapper
  - 以后的方法调用 `getMethod` 是调用 `RequestWrapper` 的
- 客户端 REST
  - 如 Postman 直接发送 `PUT`、`DELETE` 等方式请求，无需 Filter

```java
// WebMvcAutoConfiguration
@Bean
@ConditionalOnMissingBean(HiddenHttpMethodFilter.class)
@ConditionalOnProperty(
        prefix = "spring.mvc.hiddenmethod.filter",
        name = "enabled",
        matchIfMissing = false
)
public OrderedHiddenHttpMethodFilter hiddenHttpMethodFilter() {
    return new OrderedHiddenHttpMethodFilter();
}
```

```java
package org.springframework.web.filter;
public class HiddenHttpMethodFilter extends OncePerRequestFilter {
    private static final List<String> ALLOWED_METHODS =
        Collections.unmodifiableList(Arrays.asList(
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.PATCH.name()
        ));
    public static final String DEFAULT_METHOD_PARAM = "_method";
    private String methodParam = DEFAULT_METHOD_PARAM;
    // ...
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        if ("POST".equals(request.getMethod()) && request.getAttribute(
                WebUtils.ERROR_EXCEPTION_ATTRIBUTE) == null) {
            String paramValue = request.getParameter(this.methodParam);
            if (StringUtils.hasLength(paramValue)) {
                String method = paramValue.toUpperCase(Locale.ENGLISH);
                if (ALLOWED_METHODS.contains(method)) {
                    requestToUse = new HttpMethodRequestWrapper(request, method);
                }
            }
        }
        filterChain.doFilter(requestToUse, response);
    }
    // ...
}
```

- 自定义 `_method`

```java
@Configuration(proxyBeanMethods = false)
public class WebConfig {
    @Bean
    public HiddenHttpMethodFilter hiddenHttpMethodFilter() {
        HiddenHttpMethodFilter methodFilter = new HiddenHttpMethodFilter();
        methodFilter.setMethodParam("_m");
        return methodFilter;
    }
}
```

```html
<form action="/user" method="post">
  <input name="_m" type="hidden" value="put" />
  <input value="REST-PUT 提交" type="submit" />
</form>
```

### 3.2. 请求映射原理

#### 3.2.1. DispatcherServlet 关键代码

- SpringMVC 功能分析都从 `org.springframework.web.servlet.DispatcherServlet.doDispatch()` 开始

```java
package org.springframework.web.servlet;
public class DispatcherServlet extends FrameworkServlet {
    // ...
    @Override
    protected void doService(HttpServletRequest request,
                             HttpServletResponse response) throws Exception {
        // ...
        doDispatch(request, response);
        // ...
    }
    // ...
    protected void doDispatch(HttpServletRequest request,
                              HttpServletResponse response) throws Exception {
        HttpServletRequest processedRequest = request;
        HandlerExecutionChain mappedHandler = null;
        // ...
        // Determine handler for the current request.
        mappedHandler = getHandler(processedRequest);
        // ...
        // Determine handler adapter for the current request.
        HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
        // ...
        if (!mappedHandler.applyPreHandle(processedRequest, response)) {
            return;
        }
        // Actually invoke the handler.
        mv = ha.handle(processedRequest, response, mappedHandler.getHandler());
        // ...
        mappedHandler.applyPostHandle(processedRequest, response, mv);
        // ...
    }
    // ...
}
```

```java
package org.springframework.web.servlet;
public abstract class FrameworkServlet extends HttpServletBean
        implements ApplicationContextAware {
    // ...
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpMethod httpMethod = HttpMethod.resolve(request.getMethod());
        if (httpMethod == HttpMethod.PATCH || httpMethod == null) {
            processRequest(request, response);
        }
        else {
            super.service(request, response);
        }
    }
    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
    @Override
    protected final void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
    @Override
    protected final void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
    @Override
    protected final void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
    // ...
    protected final void processRequest(HttpServletRequest request,
                                        HttpServletResponse response)
            throws ServletException, IOException {
        // ...
        doService(request, response);
        // ...
    }
    // ...
    protected abstract void doService(HttpServletRequest request,
                                      HttpServletResponse response) throws Exception;
    // ...
}
```

```java
package org.springframework.web.servlet;
public abstract class HttpServletBean extends HttpServlet
        implements EnvironmentCapable, EnvironmentAware {
    // ...
    @Override
    public final void init() throws ServletException {
        // ...
        // Let subclasses do whatever initialization they like.
        initServletBean();
    }
    // ...
}
```

![DispatcherServlet](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/DispatcherServlet.4ui7sfx5w6q0.jpg)

#### 3.2.2. Servlet 基础

```java
package javax.servlet.http;
public abstract class HttpServlet extends GenericServlet {
    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";
    // ...
    @Override
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException {
        HttpServletRequest  request;
        HttpServletResponse response;
        try {
            request = (HttpServletRequest) req;
            response = (HttpServletResponse) res;
        } catch (ClassCastException e) {
            throw new ServletException(lStrings.getString("http.non_http"));
        }
        service(request, response);
    }
    // ...
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String method = req.getMethod();
        if (method.equals(METHOD_GET)) {
            // ...
            doGet(req, resp);
        } else if (method.equals(METHOD_HEAD)) {
            // ...
            doHead(req, resp);
        } else if (method.equals(METHOD_POST)) {
            doPost(req, resp);
        } else if (method.equals(METHOD_PUT)) {
            doPut(req, resp);
        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);
        } else if (method.equals(METHOD_OPTIONS)) {
            doOptions(req,resp);
        } else if (method.equals(METHOD_TRACE)) {
            doTrace(req,resp);
        } else {
            // ...
        }
    }
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // ...
        sendMethodNotAllowed(req, resp, msg);
    }
    protected void doHead(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        // ...
        sendMethodNotAllowed(req, resp, msg);
    }
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
        // ...
        sendMethodNotAllowed(req, resp, msg);
    }
    protected void doDelete(HttpServletRequest req,
                            HttpServletResponse resp)
        throws ServletException, IOException {
        // ...
        sendMethodNotAllowed(req, resp, msg);
    }
    // ...
}
```

```java
package javax.servlet;
public abstract class GenericServlet
        implements Servlet, ServletConfig, java.io.Serializable {
    // ...
    @Override
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        this.init();
    }
    public void init() throws ServletException {
        // NOOP by default
    }
    // ...
    @Override
    public abstract void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;
    // ...
    @Override
    public void destroy() {
        // NOOP by default
    }
    // ...
}
```

```java
package javax.servlet;
public interface Servlet {
    public void init(ServletConfig config) throws ServletException;
    public void service(ServletRequest req, ServletResponse res)
            throws ServletException, IOException;
    public void destroy();
}
```

#### 3.2.3. 处理器映射器

- 找到当前请求使用哪个 handler（Controller 的方法）处理
- HandlerMapping：处理器映射器，`/xxx` -> `xxxx`
- 所有的请求映射都在 HandlerMapping 中
  - SpringBoot 自动配置欢迎页的 `WelcomePageHandlerMapping`，访问 `/` 能访问到 `index.html`
  - SpringBoot 自动配置了默认的 `RequestMappingHandlerMapping`，保存了所有 `@RequestMapping` 和 handler 的映射规则
- 请求进来，
  - 挨个尝试所有的 HandlerMapping 看是否有请求信息
  - 如果有就找到这个请求对应的 handler
  - 如果没有就是下一个 HandlerMapping
- 我们需要一些自定义的映射处理，我们也可以自己给容器中放 HandlerMapping
- 自定义 HandlerMapping 实现不同版本的 API

```java
// DispatcherServlet
protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
    if (this.handlerMappings != null) {
        for (HandlerMapping mapping : this.handlerMappings) {
            HandlerExecutionChain handler = mapping.getHandler(request);
            if (handler != null) {
                return handler;
            }
        }
    }
    return null;
}
```

```java
package org.springframework.web.servlet;
public interface HandlerMapping {
    // ...
    @Nullable
    HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception;
}
```

- 处理器映射器的实现类
  - `RequestMappingHandlerMapping`
  - `WelcomePageHandlerMapping`
  - `BeanNameUrlHandlerMapping`
  - `RouterFunctionMapping`
  - `SimpleUrlHandlerMapping`

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class RequestMappingHandlerMapping extends RequestMappingInfoHandlerMapping
        implements MatchableHandlerMapping, EmbeddedValueResolverAware {}
```

```java
package org.springframework.web.servlet.mvc.method;
public abstract class RequestMappingInfoHandlerMapping
        extends AbstractHandlerMethodMapping<RequestMappingInfo> {}
```

```java
package org.springframework.web.servlet.handler;
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping
        implements InitializingBean {
    // ...
    private final MappingRegistry mappingRegistry = new MappingRegistry();
    // ...
    @Override
    protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
        // ...
        String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
        // ...
        HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
        // ...
    }
    @Nullable
    protected HandlerMethod lookupHandlerMethod(String lookupPath,
                                                HttpServletRequest request) throws Exception {
        List<Match> matches = new ArrayList<>();
        List<T> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
        // ...
    }
    // ...
    class MappingRegistry {
        // ...
        // 保存了所有 @RequestMapping 和 handler 的映射规则
        private final Map<T, HandlerMethod> mappingLookup = new LinkedHashMap<>();
        // ...
    }
}
```

```java
package org.springframework.web.servlet.handler;
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport
        implements HandlerMapping, Ordered, BeanNameAware {
    // ...
    @Override
    @Nullable
    public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        Object handler = getHandlerInternal(request);
        // ...
        HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);
        // ...
        return executionChain;
    }
    @Nullable
    protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;
    // ...
}
```

### 3.3. 普通参数与基本注解

- 注解

```java
@RequestMapping("hello")
public String Hello(@RequestParam("username") String name) {
    return "hello";
}
```

```java
@PathVariable // 路径变量
@RequestHeader  // 获取请求头
@RequestParam  // 获取请求参数
@CookieValue  // 获取cookie值
@RequestBody  // 获取请求体
@RequestAttribute  // 获取请求域属性
@MatrixVariable  // 矩阵变量
@ModelAttribute
```

- Servlet API

```java
@RequestMapping("hello")
public String Hello(HttpSession session) {
    return "hello";
}
```

```java
WebRequest
ServletRequest
MultipartRequest
HttpSession
javax.servlet.http.PushBuilder
Principal
InputStream
Reader
HttpMethod
Locale
TimeZone
ZoneId
```

- 复杂参数

```java
@RequestMapping("hello")
public String Hello(Model model) {
    return "hello";
}
```

```java
Map
Model
Errors
BindingResult
RedirectAttributes
ServletResponse
SessionStatus
UriComponentsBuilder
ServletUriComponentsBuilder
```

- 自定义对象参数

```java
@RequestMapping("hello")
public String Hello(Person person) {
    return "hello";
}
```

### 3.4. 矩阵变量

- 查询字符串（QueryString） `@RequestParam`

```http
/cars/{path}?xxx=xxx&aaa=ccc
```

- 矩阵变量 `@MatrixVariable`

```http
/cars/sell;low=34;brand=byd;audi;yd
/boss/1/2
/boss/1;age=20/2;age=20
```

- 页面开发，cookie 禁用了，session 里面的内容怎么使用？

session.set(a, b) ---> jsessionid ---> cookie ---> 每次发请求携带

- url 重写，把 cookie 的值使用矩阵变量的方式进行传递。

```http
/abc;jsessionid=xxx
```

```java
@Override
@SuppressWarnings("deprecation")
public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.setUseSuffixPatternMatch(this.mvcProperties.getPathmatch().isUseSuffixPattern());
    configurer.setUseRegisteredSuffixPatternMatch(
            this.mvcProperties.getPathmatch().isUseRegisteredSuffixPattern());
    this.dispatcherServletPath.ifAvailable((dispatcherPath) -> {
        String servletUrlMapping = dispatcherPath.getServletUrlMapping();
        if (servletUrlMapping.equals("/") && singleDispatcherServlet()) {
            UrlPathHelper urlPathHelper = new UrlPathHelper();
            urlPathHelper.setAlwaysUseFullPath(true);
            configurer.setUrlPathHelper(urlPathHelper);
        }
    });
}
```

```java
package org.springframework.web.util;
public class UrlPathHelper {
    // ...
    // 路径的矩阵变量解析默认是禁用的
    private boolean removeSemicolonContent = true;
    // ...
}
```

```java
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig1 implements WebMvcConfigurer {
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        // 不移除;后面的内容，矩阵变量的功能就可以生效
        urlPathHelper.setRemoveSemicolonContent(false);
        configurer.setUrlPathHelper(urlPathHelper);
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig2 {
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                UrlPathHelper urlPathHelper = new UrlPathHelper();
                // 不移除;后面的内容，矩阵变量的功能就可以生效
                urlPathHelper.setRemoveSemicolonContent(false);
                configurer.setUrlPathHelper(urlPathHelper);
            }
        };
    }
}
```

### 3.5. 基本注解参数解析原理

#### 3.5.1. 处理器适配器

- `HandlerMapping` 中找到能处理请求的 `Handler`（`Controller.method()`）
- 为当前 `Handler` 找一个适配器 `HandlerAdapter`；`RequestMappingHandlerAdapter`
- 适配器执行目标方法，并确定方法参数的每一个值

```java
// DispatcherServlet.doDispatch()
// 第一步、找到handler，也就是Controller.method()
mappedHandler = getHandler(processedRequest);
```

```java
// DispatcherServlet.doDispatch()
// 第二步、找到adapter，用于执行Controller.method()
HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
```

```java
package org.springframework.web.servlet;
public interface HandlerAdapter {
    boolean supports(Object handler);
    @Nullable
    ModelAndView handle(HttpServletRequest request,
                        HttpServletResponse response,
                        Object handler) throws Exception;
    long getLastModified(HttpServletRequest request, Object handler);
}
```

```java
// DispatcherServlet.doDispatch()
// 第三步、adapter反射执行Controller.method()
mv = ha.handle(processedRequest, response, mappedHandler.getHandler());
```

- `HandlerAdapter`
  - `AbstractHandlerMethodAdapter`
    - `RequestMappingHandlerAdapter`
  - `HandlerFunctionAdapter`
  - `HttpRequestHandlerAdapter`
  - `SimpleControllerHandlerAdapter`

```java
package org.springframework.web.servlet.mvc.method;
public abstract class AbstractHandlerMethodAdapter extends WebContentGenerator
        implements HandlerAdapter, Ordered {
    // ...
    @Override
    @Nullable
    public final ModelAndView handle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler)
        throws Exception {
        return handleInternal(request, response, (HandlerMethod) handler);
    }
    @Nullable
    protected abstract ModelAndView handleInternal(HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   HandlerMethod handlerMethod)
            throws Exception;
    // ...
}
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
        implements BeanFactoryAware, InitializingBean {
    @Nullable
    private HandlerMethodArgumentResolverComposite argumentResolvers;
    @Nullable
    private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
    @Override
    protected ModelAndView handleInternal(HttpServletRequest request,
                                          HttpServletResponse response,
                                          HandlerMethod handlerMethod) throws Exception {
        // 关键！adapter执行目标方法
        mav = invokeHandlerMethod(request, response, handlerMethod);
    }
    @Nullable
    protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
                                               HttpServletResponse response,
                                               HandlerMethod handlerMethod) throws Exception {
        // ...
        ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
        // 设置所有的参数解析器
        if (this.argumentResolvers != null) {
            invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
        }
        // 设置所有的返回值解析器
        if (this.returnValueHandlers != null) {
            invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
        }
        // ...
        // 关键！方法对象反射执行
        invocableMethod.invokeAndHandle(webRequest, mavContainer);
        // ...
    }
}
```

#### 3.5.2. 参数解析器

```java
package org.springframework.web.method.support;
public interface HandlerMethodArgumentResolver {
    boolean supportsParameter(MethodParameter parameter);
    @Nullable
    Object resolveArgument(MethodParameter parameter,
                           @Nullable ModelAndViewContainer mavContainer,
                           NativeWebRequest webRequest,
                           @Nullable WebDataBinderFactory binderFactory) throws Exception;
}
```

#### 3.5.3. 返回值解析器

```java
package org.springframework.web.method.support;
public interface HandlerMethodReturnValueHandler {
    boolean supportsReturnType(MethodParameter returnType);
    void handleReturnValue(@Nullable Object returnValue,
                           MethodParameter returnType,
                           ModelAndViewContainer mavContainer,
                           NativeWebRequest webRequest) throws Exception;
}
```

#### 3.5.4. 可调用方法

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {
    // ...
    @Nullable
    private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
    // ...
    public void invokeAndHandle(ServletWebRequest webRequest,
                                ModelAndViewContainer mavContainer,
                                Object... providedArgs) throws Exception {
    Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
    setResponseStatus(webRequest);
    // ...
}
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class InvocableHandlerMethod extends HandlerMethod {
    // ...
    private HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
    // ...
    @Nullable
    public Object invokeForRequest(NativeWebRequest request,
                                   @Nullable ModelAndViewContainer mavContainer,
                                   Object... providedArgs) throws Exception {
        Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
        // ...
        return doInvoke(args);
    }
    protected Object[] getMethodArgumentValues(NativeWebRequest request,
                                               @Nullable ModelAndViewContainer mavContainer,
                                               Object... providedArgs) throws Exception {
            // ...
            args[i] = this.resolvers.resolveArgument(parameter, mavContainer, request,
                    this.dataBinderFactory);
            // ...
        }
        return args;
    }
    @Nullable
    protected Object doInvoke(Object... args) throws Exception {
        // ...
        return getBridgedMethod().invoke(getBean(), args);
        // ...
    }
}
```

```java
package org.springframework.web.method;
import java.lang.reflect.Method;
public class HandlerMethod {
    // ...
    private final Method method;
    private final Method bridgedMethod;
    // ...
}
```

#### 3.5.5. 参数解析器的处理逻辑

```java
package org.springframework.web.method.support;
public class HandlerMethodArgumentResolverComposite implements HandlerMethodArgumentResolver {
    private final List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
    private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache =
        new ConcurrentHashMap<>(256);
    // ...
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return getArgumentResolver(parameter) != null;
    }
    // ...
    @Override
    @Nullable
    public Object resolveArgument(MethodParameter parameter,
                                  @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  @Nullable WebDataBinderFactory binderFactory)
            throws Exception {
        // 找到合适的参数解析器
        HandlerMethodArgumentResolver resolver = getArgumentResolver(parameter);
        // ...
        return resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
    }
    // ...
    @Nullable
    private HandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
        HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
        if (result == null) {
            for (HandlerMethodArgumentResolver resolver : this.argumentResolvers) {
                if (resolver.supportsParameter(parameter)) {
                    result = resolver;
                    this.argumentResolverCache.put(parameter, result);
                    break;
                }
            }
        }
        return result;
    }
}
```

#### 3.5.6. 参数解析器的抽象实现类

```java
package org.springframework.web.method.annotation;
public abstract class AbstractNamedValueMethodArgumentResolver
        implements HandlerMethodArgumentResolver {
    // ...
    @Override
    @Nullable
    public final Object resolveArgument(MethodParameter parameter,
                                        @Nullable ModelAndViewContainer mavContainer,
                                        NativeWebRequest webRequest,
                                        @Nullable WebDataBinderFactory binderFactory)
            throws Exception {
        Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);
        // 各种处理逻辑，调用（子类必须重写的）抽象方法、可供子类重写的方法
        return arg;
    }
    // 供子类重写
    @Nullable
    protected abstract Object resolveName(String name,
                                          MethodParameter parameter,
                                          NativeWebRequest request)
            throws Exception;
    // ...
}
```

#### 3.5.7. 参数解析器的实现类

- `MapMethodProcessor`
- `PathVariableMapMethodArgumentResolver`
- `ErrorsMethodArgumentResolver`
- `AbstractNamedValueMethodArgumentResolver`
  - `RequestHeaderMethodArgumentResolver`
  - `RequestAttributeMethodArgumentResolver`
  - `RequestParamMethodArgumentResolver`
  - `AbstractCookieValueMethodArgumentResolver`
    - `ServletCookieValueMethodArgumentResolver`
  - `SessionAttributeMethodArgumentResolver`
  - `MatrixVariableMethodArgumentResolver`
  - `ExpressionValueMethodArgumentResolver`
  - `PathVariableMethodArgumentResolver`
- `RequestHeaderMapMethodArgumentResolver`
- `ServletResponseMethodArgumentResolver`
- `ModelMethodProcessor`
- `ModelAttributeMethodProcessor`
  - `ServletModelAttributeMethodProcessor`
- `SessionStatusMethodArgumentResolver`
- `RequestParamMapMethodArgumentResolver`
- `AbstractMessageConverterMethodArgumentResolver`
  - `RequestPartMethodArgumentResolver`
  - `AbstractMessageConverterMethodProcessor`
    - `RequestResponseBodyMethodProcessor`
    - `HttpEntityMethodProcessor`
- `AbstractWebArgumentResolverAdapter`
  - `ServletWebArgumentResolverAdapter`
- `UriComponentsBuilderMethodArgumentResolver`
- `ServletRequestMethodArgumentResolver`
- `HandlerMethodArgumentResolverComposite`
- `RedirectAttributesMethodArgumentResolver`
- `MatrixVariableMapMethodArgumentResolver`

### 3.6. Servlet API 参数解析原理

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class ServletRequestMethodArgumentResolver implements HandlerMethodArgumentResolver {
    // ...
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> paramType = parameter.getParameterType();
        return (WebRequest.class.isAssignableFrom(paramType) ||
            ServletRequest.class.isAssignableFrom(paramType) ||
            MultipartRequest.class.isAssignableFrom(paramType) ||
            HttpSession.class.isAssignableFrom(paramType) ||
            (pushBuilder != null && pushBuilder.isAssignableFrom(paramType)) ||
            Principal.class.isAssignableFrom(paramType) ||
            InputStream.class.isAssignableFrom(paramType) ||
            Reader.class.isAssignableFrom(paramType) ||
            HttpMethod.class == paramType ||
            Locale.class == paramType ||
            TimeZone.class == paramType ||
            ZoneId.class == paramType);
    }
    // ...
}
```

### 3.7. Map 和 Model 参数解析原理

- Map、Model（数据会被放在 request 的请求域，相当于 `request.setAttribute()`）
- RedirectAttributes（重定向携带数据）
- ServletResponse（响应）

```java
@Controller
public class ServletController {
    @GetMapping("/servlet1")
    public String getServlet1(Map<String, Object> map,
                              Model model,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        map.put("hello", "world666");
        model.addAttribute("world", "hello666");
        request.setAttribute("message", "hello world");
        Cookie cookie = new Cookie("c1", "v1");
        response.addCookie(cookie);
        return "forward:/servlet1/result";
    }
    @ResponseBody
    @GetMapping("/servlet1/result")
    public Map<String, Object> getServlet1Result(@RequestAttribute("hello") String hello,
                                                 @RequestAttribute("world") String world,
                                                 @RequestAttribute("message") String message) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("hello", hello);
        map.put("world", world);
        map.put("message", message);
        return map;
    }
}
```

#### 3.7.1. Map 和 Model 参数解析器

- Map 和 Model 底层调用同一个方法，获取到的 map 对象和 model 对象是同一个对象

```java
// Map参数解析器、Map返回值解析器
package org.springframework.web.method.annotation;
public class MapMethodProcessor
        implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Map.class.isAssignableFrom(parameter.getParameterType()) &&
                parameter.getParameterAnnotations().length == 0;
    }
    @Override
    @Nullable
    public Object resolveArgument(MethodParameter parameter,
                                  @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  @Nullable WebDataBinderFactory binderFactory)
            throws Exception {
        // ...
        return mavContainer.getModel();
    }
    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return Map.class.isAssignableFrom(returnType.getParameterType());
    }
    // ...
}
```

```java
// Model参数解析器、Model返回值解析器
package org.springframework.web.method.annotation;
public class ModelMethodProcessor
        implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Model.class.isAssignableFrom(parameter.getParameterType());
    }
    @Override
    @Nullable
    public Object resolveArgument(MethodParameter parameter,
                                  @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  @Nullable WebDataBinderFactory binderFactory)
            throws Exception {
        // ...
        return mavContainer.getModel();
    }
    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return Model.class.isAssignableFrom(returnType.getParameterType());
    }
    // ...
}
```

#### 3.7.2. 模型和视图容器

```java
package org.springframework.web.method.support;
public class ModelAndViewContainer {
    // ...
    private final ModelMap defaultModel = new BindingAwareModelMap();
    // ...
    public ModelMap getModel() {
        // ...
        return this.defaultModel;
        // ...
    }
    // ...
}
```

```java
package org.springframework.validation.support;
public class BindingAwareModelMap extends ExtendedModelMap {}
```

```java
package org.springframework.ui;
public class ExtendedModelMap extends ModelMap implements Model {}
```

```java
package org.springframework.ui;
public class ModelMap extends LinkedHashMap<String, Object> {}
```

```java
package java.util;
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {}
```

#### 3.7.3. 数据是如何放到请求域中的

```java
// DispatcherServlet.doDispatch()
mv = ha.handle(processedRequest, response, mappedHandler.getHandler());
// 关键之二！拿到ModelAndView数据后，这里执行拦截器
mappedHandler.applyPostHandle(processedRequest, response, mv);
// 关键之三！将ModelAndView数据设置到request的attribute中
processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
```

```java
// AbstractHandlerMethodAdapter.handle()
return handleInternal(request, response, (HandlerMethod) handler);
```

```java
// RequestMappingHandlerAdapter.handleInternal()
mav = invokeHandlerMethod(request, response, handlerMethod);
// RequestMappingHandlerAdapter.invokeHandlerMethod()
invocableMethod.invokeAndHandle(webRequest, mavContainer);
// 关键之一！适配器反射执行目标方法之后，Map和Model数据放在ModelAndViewContainer对象中
return getModelAndView(mavContainer, modelFactory, webRequest);
```

```java
// ServletInvocableHandlerMethod.invokeAndHandle()
Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
setResponseStatus(webRequest);
```

```java
// InvocableHandlerMethod.invokeForRequest()
Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
// ...
return doInvoke(args);
// InvocableHandlerMethod.doInvoke()
return getBridgedMethod().invoke(getBean(), args);
```

#### 3.7.4. 关键之一

- Map 和 Model 数据放在 `ModelAndViewContainer` 对象中

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
        implements BeanFactoryAware, InitializingBean {
    @Nullable
    private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
                                         ModelFactory modelFactory,
                                         NativeWebRequest webRequest) throws Exception {
        // ...
        ModelMap model = mavContainer.getModel();
        ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
        // ...
        return mav;
    }
}
```

#### 3.7.5. 关键之三

- 将 ModelAndView 数据设置到 request 的 attribute 中

```java
package org.springframework.web.servlet;
public class DispatcherServlet extends FrameworkServlet {
    // ...
    private void processDispatchResult(HttpServletRequest request,
                                       HttpServletResponse response,
                                       @Nullable HandlerExecutionChain mappedHandler,
                                       @Nullable ModelAndView mv,
                                       @Nullable Exception exception)
            throws Exception {
        // ...
        render(mv, request, response);
        // ...
    }
    // ...
    protected void render(ModelAndView mv,
                          HttpServletRequest request,
                          HttpServletResponse response) throws Exception {
        // ...
        view.render(mv.getModelInternal(), request, response);
        // ...
    }
}
```

#### 3.7.6. 视图渲染

```java
package org.springframework.web.servlet;
public interface View {
    // ...
    void render(@Nullable Map<String, ?> model,
                HttpServletRequest request,
                HttpServletResponse response)
            throws Exception;
}
```

```java
package org.springframework.web.servlet.view;
public abstract class AbstractView extends WebApplicationObjectSupport
        implements View, BeanNameAware {
    // ...
    @Override
    public void render(@Nullable Map<String, ?> model,
                       HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        // ...
        Map<String, Object> mergedModel = createMergedOutputModel(model, request, response);
        prepareResponse(request, response);
        renderMergedOutputModel(mergedModel, getRequestToExpose(request), response);
    }
    // ...
    protected abstract void renderMergedOutputModel(Map<String, Object> model,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response)
            throws Exception;
    protected void exposeModelAsRequestAttributes(Map<String, Object> model,
                                                  HttpServletRequest request)
            throws Exception {
        model.forEach((name, value) -> {
            if (value != null) {
                request.setAttribute(name, value);
            }
            else {
                request.removeAttribute(name);
            }
        });
    }
    // ...
}
```

```java
package org.springframework.web.servlet.view;
public abstract class AbstractUrlBasedView extends AbstractView implements InitializingBean {}
```

```java
package org.springframework.web.servlet.view;
public class InternalResourceView extends AbstractUrlBasedView {
    @Override
    protected void renderMergedOutputModel(Map<String, Object> model,
                                           HttpServletRequest request,
                                           HttpServletResponse response)
            throws Exception {
        // Expose the model object as request attributes.
        exposeModelAsRequestAttributes(model, request);
        // ...
    }
}
```

### 3.8. 自定义对象参数解析原理

```html
<p>测试自定义对象</p>
<form action="/object" method="post">
  姓名：<input name="userName" type="text" value="zhangsan" /><br />
  年龄：<input name="age" type="number" value="23" /><br />
  生日：<input name="birth" type="text" value="2021/01/01" /><br />
  宠物姓名：<input name="pet.name" type="text" value="小猫" /><br />
  宠物年龄：<input name="pet.age" type="number" value="2" /><br />
  <input value="提交" type="submit" />
</form>
```

```java
@RestController
public class ObjectController {
    @PostMapping("/object")
    public Person getObject(Person person) {
        return person;
    }
}
```

#### 3.8.1. 找到自定义对象的解析器

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class ServletModelAttributeMethodProcessor extends ModelAttributeMethodProcessor {}
```

```java
package org.springframework.web.method.annotation;
public class ModelAttributeMethodProcessor
        implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return (parameter.hasParameterAnnotation(ModelAttribute.class)
                || (this.annotationNotRequired
                        && !BeanUtils.isSimpleProperty(parameter.getParameterType())));
    }
    @Override
    @Nullable
    public final Object resolveArgument(MethodParameter parameter,
                                        @Nullable ModelAndViewContainer mavContainer,
                                        NativeWebRequest webRequest,
                                        @Nullable WebDataBinderFactory binderFactory)
            throws Exception {}
    // ...
}
```

```java
package org.springframework.beans;
public abstract class BeanUtils {
    // ...
    public static boolean isSimpleProperty(Class<?> type) {
        // ...
        return isSimpleValueType(type)
                || (type.isArray()
                        && isSimpleValueType(type.getComponentType()));
    }
    public static boolean isSimpleValueType(Class<?> type) {
        return (Void.class != type && void.class != type &&
                (ClassUtils.isPrimitiveOrWrapper(type) ||
                Enum.class.isAssignableFrom(type) ||
                CharSequence.class.isAssignableFrom(type) ||
                Number.class.isAssignableFrom(type) ||
                Date.class.isAssignableFrom(type) ||
                Temporal.class.isAssignableFrom(type) ||
                URI.class == type ||
                URL.class == type ||
                Locale.class == type ||
                Class.class == type));
    }
    // ...
}
```

```java
package java.lang;
public final class Class<T>
        implements java.io.Serializable, GenericDeclaration, Type, AnnotatedElement {
    // ...
    public native boolean isArray();
    // ...
}
```

#### 3.8.2. 解析自定义对象的过程

```java
// ModelAttributeMethodProcessor
@Override
@Nullable
public final Object resolveArgument(MethodParameter parameter,
                                    @Nullable ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    @Nullable WebDataBinderFactory binderFactory)
    // ...
    // Create attribute instance
    attribute = createAttribute(name, parameter, binderFactory, webRequest);
    // ...
    // Bean property binding and validation;
    WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, name);
    bindRequestParameters(binder, webRequest);  // 关键！绑定属性操作
    bindingResult = binder.getBindingResult();
    // ...
    // Add resolved attribute and BindingResult at the end of the model
    Map<String, Object> bindingResultModel = bindingResult.getModel();
    mavContainer.removeAttributes(bindingResultModel);
    mavContainer.addAllAttributes(bindingResultModel);
    return attribute;
}
```

```java
// ModelAttributeMethodProcessor
protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
    ((WebRequestDataBinder) binder).bind(request);
}
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class ServletModelAttributeMethodProcessor extends ModelAttributeMethodProcessor {
    // ...
    @Override
    protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
        // ...
        servletBinder.bind(servletRequest);  // 关键！绑定属性操作
    }
}
```

#### 3.8.3. 数据绑定器

```java
package org.springframework.web.bind;
public class ServletRequestDataBinder extends WebDataBinder {
    // ...
    public void bind(ServletRequest request) {
        MutablePropertyValues mpvs = new ServletRequestParameterPropertyValues(request);
        // ...
        doBind(mpvs);  // 关键！绑定属性操作
    }
}
```

```java
package org.springframework.web.bind;
public class WebDataBinder extends DataBinder {
    // ...
    @Override
    protected void doBind(MutablePropertyValues mpvs) {
        // ...
        super.doBind(mpvs);  // 关键！绑定属性操作
    }
    // ...
}
```

```java
package org.springframework.validation;
public class DataBinder implements PropertyEditorRegistry, TypeConverter {
    // ...
    @Nullable
    private final Object target;
    // ...
    @Nullable
    private ConversionService conversionService;  // 关键！属性类型转换器
    // ...
    protected void doBind(MutablePropertyValues mpvs) {
        // ...
        applyPropertyValues(mpvs);  // 关键！绑定属性操作
    }
    // ...
    protected void applyPropertyValues(MutablePropertyValues mpvs) {
        // ...
        // Bind request parameters onto target object.
        getPropertyAccessor().setPropertyValues(mpvs, isIgnoreUnknownFields(),
                isIgnoreInvalidFields());  // 关键！绑定属性操作
        // ...
    }
    // ...
}
```

#### 3.8.4. 属性访问器

```java
package org.springframework.beans;
public abstract class AbstractPropertyAccessor extends TypeConverterSupport
        implements ConfigurablePropertyAccessor {
    // ...
    @Override
    public void setPropertyValues(PropertyValues pvs,
                                  boolean ignoreUnknown,
                                  boolean ignoreInvalid) throws BeansException {
        for (PropertyValue pv : propertyValues) {
            // ...
            // This method may throw any BeansException, which won't be caught
            // here, if there is a critical failure such as no matching field.
            // We can attempt to deal only with less serious exceptions.
            setPropertyValue(pv);
            // ...
        }
    }
    // ...
}
```

```java
package org.springframework.beans;
public abstract class AbstractNestablePropertyAccessor extends AbstractPropertyAccessor {
    // ...
    @Override
    public void setPropertyValue(PropertyValue pv) throws BeansException {
        PropertyTokenHolder tokens = (PropertyTokenHolder) pv.resolvedTokens;
        // ...
        nestedPa.setPropertyValue(tokens, pv);
        // setPropertyValue(tokens, pv);
    }
    protected void setPropertyValue(PropertyTokenHolder tokens, PropertyValue pv)
            throws BeansException {
        // processKeyedProperty(tokens, pv);
        processLocalProperty(tokens, pv);
    }
    // ...
    private void processLocalProperty(PropertyTokenHolder tokens, PropertyValue pv) {
        // ...
        // String转换成目标类型
        valueToApply = convertForProperty(
                tokens.canonicalName,
                oldValue,
                originalValue,
                ph.toTypeDescriptor());
        // ...
        // 反射设置属性值
        ph.setValue(valueToApply);
        // ...
    }
}
```

#### 3.8.5. 类型转换服务

![WebConversionService](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/WebConversionService.1oa9eedtc7xc.jpg)

```java
package org.springframework.boot.autoconfigure.web.format;
public class WebConversionService extends DefaultFormattingConversionService {}
```

```java
package org.springframework.format.support;
public class DefaultFormattingConversionService extends FormattingConversionService {}
```

```java
package org.springframework.format.support;
public class FormattingConversionService extends GenericConversionService
        implements FormatterRegistry, EmbeddedValueResolverAware {}
```

```java
package org.springframework.core.convert.support;
public class GenericConversionService implements ConfigurableConversionService {
    // ...
    private final Converters converters = new Converters();
    private final Map<ConverterCacheKey, GenericConverter> converterCache = new ConcurrentReferenceHashMap<>(64);
    // ...
    private static class Converters {
        private final Set<GenericConverter> globalConverters = new LinkedHashSet<>();
        private final Map<ConvertiblePair, ConvertersForPair> converters = new LinkedHashMap<>(36);
        // ...
        @Nullable
        public GenericConverter find(TypeDescriptor sourceType, TypeDescriptor targetType) {
            // Search the full type hierarchy
            List<Class<?>> sourceCandidates = getClassHierarchy(sourceType.getType());
            List<Class<?>> targetCandidates = getClassHierarchy(targetType.getType());
            for (Class<?> sourceCandidate : sourceCandidates) {
                for (Class<?> targetCandidate : targetCandidates) {
                    ConvertiblePair convertiblePair = new ConvertiblePair(sourceCandidate, targetCandidate);
                    GenericConverter converter = getRegisteredConverter(sourceType,
                                                                        targetType,
                                                                        convertiblePair);
                    if (converter != null) {
                        return converter;
                    }
                }
            }
            return null;
        }
        // ...
    }
    // ...
    private static class ConvertersForPair {
        private final LinkedList<GenericConverter> converters = new LinkedList<>();
        // ...
    }
    // ...
}
```

```java
package org.springframework.core.convert.support;
public interface ConfigurableConversionService
        extends ConversionService, ConverterRegistry {}
```

```java
package org.springframework.core.convert;
public interface ConversionService {
    boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType);
    boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType);
    @Nullable
    <T> T convert(@Nullable Object source, Class<T> targetType);
    @Nullable
    Object convert(@Nullable Object source,
                   @Nullable TypeDescriptor sourceType,
                   TypeDescriptor targetType);
}
```

#### 3.8.6. 类型转换器

```java
package org.springframework.core.convert.converter;
public interface GenericConverter {
    @Nullable
    Set<ConvertiblePair> getConvertibleTypes();
    @Nullable
    Object convert(@Nullable Object source,
                   TypeDescriptor sourceType,
                   TypeDescriptor targetType);
    // ...
    final class ConvertiblePair {
        private final Class<?> sourceType;
        private final Class<?> targetType;
        // ...
    }
}
```

```java
package org.springframework.core.convert.converter;
@FunctionalInterface
public interface Converter<S, T> {
    @Nullable
    T convert(S source);
}
```

### 3.9. 自定义类型转换器

```html
<p>测试自定义类型转换器</p>
<form action="/convert" method="post">
  姓名：<input name="userName" type="text" value="zhangsan" /><br />
  年龄：<input name="age" type="number" value="23" /><br />
  生日：<input name="birth" type="text" value="2021/01/01" /><br />
  宠物姓名：<input name="pet" type="text" value="阿猫,3" /><br />
  <input value="提交" type="submit" />
</form>
```

```java
@RestController
public class ConverterController {
    @PostMapping("/convert")
    public Person getObject(Person person) {
        return person;
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig2 {
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new Converter<String, Pet>() {
                    @Override
                    public Pet convert(String source) {
                        if (!StringUtils.isEmpty(source)) {
                            // 阿猫,3
                            Pet pet = new Pet();
                            String[] splits = source.split(",");
                            pet.setName(splits[0]);
                            pet.setAge(Integer.parseInt(splits[1]));
                            return pet;
                        }
                        return null;
                    }
                });
            }
        };
    }
}
```

## 4. 数据响应与内容协商

- 响应页面
- 响应数据
  - JSON
  - XML
  - xls
  - 图片，音视频...
  - 自定义协议数据

### 4.1. 响应 JSON

- 当前项目依赖 starter-web

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

- web 场景启动器依赖 starter-json

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-json</artifactId>
  <version>2.3.4.RELEASE</version>
  <scope>compile</scope>
</dependency>
```

- json 场景启动器依赖 jackson

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.11.2</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.datatype</groupId>
  <artifactId>jackson-datatype-jdk8</artifactId>
  <version>2.11.2</version>
  <scope>compile</scope>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.datatype</groupId>
  <artifactId>jackson-datatype-jsr310</artifactId>
  <version>2.11.2</version>
  <scope>compile</scope>
</dependency>
```

### 4.2. 返回值解析器

#### 4.2.1. 处理返回值

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
        implements BeanFactoryAware, InitializingBean {
    // ...
    @Nullable
    protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
                                               HttpServletResponse response,
                                               HandlerMethod handlerMethod) throws Exception {
        // ...
        ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
        if (this.argumentResolvers != null) {
            invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
        }
        if (this.returnValueHandlers != null) {
            invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
        }
        // ...
        invocableMethod.invokeAndHandle(webRequest, mavContainer);
        // ...
    }
    // ...
}
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {
    // ...
    @Nullable
    private HandlerMethodReturnValueHandlerComposite returnValueHandlers;
    // ...
    public void invokeAndHandle(ServletWebRequest webRequest,
                                ModelAndViewContainer mavContainer,
                                Object... providedArgs) throws Exception {
    Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
    setResponseStatus(webRequest);
    // ...
    // 关键，处理返回值
    this.returnValueHandlers.handleReturnValue(
            returnValue, getReturnValueType(returnValue), mavContainer, webRequest);
    // ...
}
```

#### 4.2.2. 返回值解析器

```java
package org.springframework.web.method.support;
public class HandlerMethodReturnValueHandlerComposite implements HandlerMethodReturnValueHandler {
    // ...
    private final List<HandlerMethodReturnValueHandler> returnValueHandlers = new ArrayList<>();
    // ...
    @Override
    public void handleReturnValue(@Nullable Object returnValue,
                                  MethodParameter returnType,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest) throws Exception {
        HandlerMethodReturnValueHandler handler = selectHandler(returnValue, returnType);
        // ...
        handler.handleReturnValue(returnValue, returnType, mavContainer, webRequest);
    }
    @Nullable
    private HandlerMethodReturnValueHandler selectHandler(@Nullable Object value,
                                                          MethodParameter returnType) {
        boolean isAsyncValue = isAsyncReturnValue(value, returnType);
        for (HandlerMethodReturnValueHandler handler : this.returnValueHandlers) {
            if (isAsyncValue && !(handler instanceof AsyncHandlerMethodReturnValueHandler)) {
                continue;
            }
            if (handler.supportsReturnType(returnType)) {
                return handler;
            }
        }
        return null;
    }
    // ...
}
```

```java
package org.springframework.web.method.support;
public interface HandlerMethodReturnValueHandler {
    boolean supportsReturnType(MethodParameter returnType);
    void handleReturnValue(@Nullable Object returnValue,
                           MethodParameter returnType,
                           ModelAndViewContainer mavContainer,
                           NativeWebRequest webRequest) throws Exception;
}
```

- `ViewNameMethodReturnValueHandler`
- `MapMethodProcessor`
- `ViewMethodReturnValueHandler`
- `StreamingResponseBodyReturnValueHandler`
- `DeferredResultMethodReturnValueHandler`
- `HandlerMethodReturnValueHandlerComposite`
- `HttpHeadersReturnValueHandler`
- `CallableMethodReturnValueHandler`
- `ModelMethodProcessor`
- `ModelAttributeMethodProcessor`
  - `ServletModelAttributeMethodProcessor`
- `ResponseBodyEmitterReturnValueHandler`
- `ModelAndViewMethodReturnValueHandler`
- `ModelAndViewResolverMethodReturnValueHandler`
- `AbstractMessageConverterMethodProcessor`
  - `RequestResponseBodyMethodProcessor`
  - `HttpEntityMethodProcessor`
- `AsyncHandlerMethodReturnValueHandler`
- `AsyncTaskMethodReturnValueHandler`

```java
// ModelAndView
ModelAndViewMethodReturnValueHandler
// Model
ModelMethodProcessor
// View
ViewMethodReturnValueHandler
// ResponseEntity || ResponseBodyEmitter
ResponseBodyEmitterReturnValueHandler
// StreamingResponseBody || ResponseEntity
StreamingResponseBodyReturnValueHandler
// HttpEntity || !RequestEntity
HttpEntityMethodProcessor
// HttpHeaders
HttpHeadersReturnValueHandler
// Callable
CallableMethodReturnValueHandler
// DeferredResult || ListenableFuture || CompletionStage
DeferredResultMethodReturnValueHandler
// WebAsyncTask
AsyncTaskMethodReturnValueHandler
// ModelAttribute
ModelAttributeMethodProcessor  // annotationNotRequired = false
// ResponseBody
RequestResponseBodyMethodProcessor  // bingo!
// void || CharSequence
ViewNameMethodReturnValueHandler
// Map
MapMethodProcessor
// ModelAttribute
ModelAttributeMethodProcessor  // annotationNotRequired = true
```

#### 4.2.3. 返回值的处理过程

1. 返回值处理器判断是否支持这种类型返回值 `supportsReturnType`
2. 返回值处理器调用 `handleReturnValue` 进行处理
3. `RequestResponseBodyMethodProcessor` 可以处理返回值标了 `@ResponseBody` 注解的
   1. 利用 `MessageConverters` 进行处理，将数据写为 json
      1. 内容协商（浏览器默认会以请求头的方式告诉服务器他能接受什么样的内容类型）
      2. 服务器最终根据自己自身的能力，决定服务器能生产出什么样内容类型的数据(内容协商)
      3. SpringMVC 会挨个遍历所有容器底层的 `HttpMessageConverter`，看谁能处理
         1. 得到 `MappingJackson2HttpMessageConverter` 可以将对象写为 json
         2. 利用 `MappingJackson2HttpMessageConverter` 将对象转为 json 再写出去

```java
package org.springframework.web.servlet.mvc.method.annotation;
public class RequestResponseBodyMethodProcessor extends AbstractMessageConverterMethodProcessor {
    // ...
    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return (AnnotatedElementUtils.hasAnnotation(
                returnType.getContainingClass(), ResponseBody.class) ||
            returnType.hasMethodAnnotation(ResponseBody.class));
    }
    // ...
    @Override
    public void handleReturnValue(@Nullable Object returnValue,
                                  MethodParameter returnType,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest)
        throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
        mavContainer.setRequestHandled(true);
        ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
        ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
        // Try even with null return value. ResponseBodyAdvice could get involved.
        // 关键！使用消息转换器进行写出操作
        writeWithMessageConverters(returnValue, returnType, inputMessage, outputMessage);
    }
    // ...
}
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public abstract class AbstractMessageConverterMethodProcessor
        extends AbstractMessageConverterMethodArgumentResolver
        implements HandlerMethodReturnValueHandler {
    // ...
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> void writeWithMessageConverters(@Nullable T value,
                                                  MethodParameter returnType,
                                                  ServletServerHttpRequest inputMessage,
                                                  ServletServerHttpResponse outputMessage)
        throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {
        Object body;
        Class<?> valueType;
        Type targetType;
        // 第一步，判断是否是字符串
        if (value instanceof CharSequence) {
            // ...
        }
        else {
            // ...
        }
        // 第二步，判断是否是资源类型
        if (isResourceType(value, returnType)) {
            // ...
        }
        // 第三步，内容协商
        MediaType selectedMediaType = null;
        MediaType contentType = outputMessage.getHeaders().getContentType();
        boolean isContentTypePreset = contentType != null && contentType.isConcrete();
        if (isContentTypePreset) {
            // ...
        }
        else {
            HttpServletRequest request = inputMessage.getServletRequest();
            List<MediaType> acceptableTypes = getAcceptableMediaTypes(request);
            List<MediaType> producibleTypes = getProducibleMediaTypes(request, valueType, targetType);
            // ...
            List<MediaType> mediaTypesToUse = new ArrayList<>();
            for (MediaType requestedType : acceptableTypes) {
                for (MediaType producibleType : producibleTypes) {
                    if (requestedType.isCompatibleWith(producibleType)) {
                        mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
                    }
                }
            }
            // ...
        }
        // 第四步，找到消息转换器
        if (selectedMediaType != null) {
            // ...
            for (HttpMessageConverter<?> converter : this.messageConverters) {
                // ...
            }
            // ...
            return result;
        }
        // ...
    }
    // ...
}
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public abstract class AbstractMessageConverterMethodArgumentResolver
        implements HandlerMethodArgumentResolver {
    // ...
    protected final List<HttpMessageConverter<?>> messageConverters;
    // ...
}
```

#### 4.2.4. 消息转换器

```java
package org.springframework.http.converter;
public interface HttpMessageConverter<T> {
    boolean canRead(Class<?> clazz, @Nullable MediaType mediaType);
    boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType);
    List<MediaType> getSupportedMediaTypes();
    T read(Class<? extends T> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException;
    void write(T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException;
}
```

- `AbstractHttpMessageConverter`
  - `SourceHttpMessageConverter`
  - `ResourceHttpMessageConverter`
  - `ByteArrayHttpMessageConverter`
  - `AbstractXmlHttpMessageConverter`
    - `AbstractJaxb2HttpMessageConverter`
      - `Jaxb2CollectionHttpMessageConverter`
      - `Jaxb2RootElementHttpMessageConverter`
    - `MarshallingHttpMessageConverter`
  - `ObjectToStringHttpMessageConverter`
  - `AbstractWireFeedHttpMessageConverter`
    - `RssChannelHttpMessageConverter`
    - `AtomFeedHttpMessageConverter`
  - `AbstractGenericHttpMessageConverter`
    - `AbstractJsonHttpMessageConverter`
      - `GsonHttpMessageConverter`
      - `JsonbHttpMessageConverter`
    - `AbstractJackson2HttpMessageConverter`
      - `MappingJackson2SmileHttpMessageConverter`
      - `MappingJackson2HttpMessageConverter`
      - `MappingJackson2CborHttpMessageConverter`
      - `MappingJackson2XmlHttpMessageConverter`
    - `ResourceRegionHttpMessageConverter`
  - `ProtobufHttpMessageConverter`
    - `ProtobufJsonFormatHttpMessageConverter`
  - `StringHttpMessageConverter`
- `FormHttpMessageConverter`
  - `AllEncompassingFormHttpMessageConverter`
- `BufferedImageHttpMessageConverter`
- `GenericHttpMessageConverter`
  - `Jaxb2CollectionHttpMessageConverter`
  - `AbstractGenericHttpMessageConverter`
    - `AbstractJsonHttpMessageConverter`
      - `GsonHttpMessageConverter`
      - `JsonbHttpMessageConverter`
    - `AbstractJackson2HttpMessageConverter`
      - `MappingJackson2SmileHttpMessageConverter`
      - `MappingJackson2HttpMessageConverter`
      - `MappingJackson2CborHttpMessageConverter`
      - `MappingJackson2XmlHttpMessageConverter`
    - `ResourceRegionHttpMessageConverter`

```java
// Byte
ByteArrayHttpMessageConverter
// String
StringHttpMessageConverter  // defaultCharset = UTF-8
// String
StringHttpMessageConverter  // defaultCharset = ISO-8859-1
// Resource
ResourceHttpMessageConverter
// ResourceRegion
ResourceRegionHttpMessageConverter
// DOMSource.class || SAXSource.class || StAXSource.class || StreamSource.class || Source.class
SourceHttpMessageConverter
// MultiValueMap
AllEncompassingFormHttpMessageConverter
// true
MappingJackson2HttpMessageConverter
// true
MappingJackson2HttpMessageConverter
// 支持注解方式xml处理的
Jaxb2RootElementHttpMessageConverter
```

- 最终 `MappingJackson2HttpMessageConverter` 把对象转为 JSON（利用底层的 jackson 的 `objectMapper` 转换的）

### 4.3. 内容协商

- 根据客户端接收能力不同，返回不同媒体类型的数据

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-xml</artifactId>
</dependency>
```

![消息转换器](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/消息转换器.29skybu864ys.png)

1. 判断当前响应头中是否已经有确定的媒体类型。MediaType
2. 获取客户端（PostMan、浏览器）支持接收的内容类型【application/xml】
   1. 获取客户端 Accept 请求头字段
   2. contentNegotiationManager 内容协商管理器 默认使用基于请求头的策略
   3. HeaderContentNegotiationStrategy 确定客户端可以接收的内容类型
3. 遍历循环所有当前系统的 MessageConverter，看谁支持操作这个对象【Person】
4. 找到支持操作 Person 的 converter，把 converter 支持的媒体类型统计出来
5. 客户端需要【application/xml】，服务端能力【10 种、json、xml】
6. 进行内容协商的最佳匹配媒体类型
7. 用 支持 将对象转为 最佳匹配媒体类型 的 converter，调用这个 converter 进行转换

```java
List<MediaType> acceptableTypes = getAcceptableMediaTypes(request);
List<MediaType> producibleTypes = getProducibleMediaTypes(request, valueType, targetType);
```

```java
List<MediaType> mediaTypesToUse = new ArrayList<>();
for (MediaType requestedType : acceptableTypes) {
    for (MediaType producibleType : producibleTypes) {
        if (requestedType.isCompatibleWith(producibleType)) {
            mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
        }
    }
}
```

- 浏览器发送请求时的内容协商

```properties
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9
```

![浏览器发送请求时的内容协商](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/浏览器发送请求时的内容协商.4oz7xy9gsho0.png)

- Postman 请求时的内容协商

```properties
Accept: application/json
```

![Postman请求时的内容协商](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/Postman请求时的内容协商.497o9ntdhyo0.png)

- 开启请求参数内容协商模式

```yaml
spring:
  mvc:
    contentnegotiation:
      favor-parameter: true
```

```http
http://localhost:8080/response/person?format=json
```

```http
http://localhost:8080/response/person?format=xml
```

### 4.4. 自定义消息转换器

- 实现多协议数据兼容。json、xml、x-guigu

1. `@ResponseBody` 响应数据出去 调用 `RequestResponseBodyMethodProcessor` 处理
2. `Processor` 处理方法返回值。通过 `MessageConverter` 处理
3. 所有 `MessageConverter` 合起来可以支持各种媒体类型数据的操作（读、写）
4. 内容协商找到最终的 `messageConverter`

```java
package org.springframework.web.servlet.config.annotation;
public class WebMvcConfigurationSupport
        implements ApplicationContextAware, ServletContextAware {
    private static final boolean romePresent;
    private static final boolean jaxb2Present;
    private static final boolean jackson2Present;
    private static final boolean jackson2XmlPresent;
    private static final boolean jackson2SmilePresent;
    private static final boolean jackson2CborPresent;
    private static final boolean gsonPresent;
    private static final boolean jsonbPresent;
    static {
        ClassLoader classLoader = WebMvcConfigurationSupport.class.getClassLoader();
        romePresent = ClassUtils.isPresent("com.rometools.rome.feed.WireFeed", classLoader);
        jaxb2Present = ClassUtils.isPresent("javax.xml.bind.Binder", classLoader);
        jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) &&
                ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
        jackson2XmlPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper", classLoader);
        jackson2SmilePresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory", classLoader);
        jackson2CborPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory", classLoader);
        gsonPresent = ClassUtils.isPresent("com.google.gson.Gson", classLoader);
        jsonbPresent = ClassUtils.isPresent("javax.json.bind.Jsonb", classLoader);
    }
    // ...
    protected final void addDefaultHttpMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
        messageConverters.add(new ByteArrayHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());
        messageConverters.add(new ResourceHttpMessageConverter());
        messageConverters.add(new ResourceRegionHttpMessageConverter());
        try {
            messageConverters.add(new SourceHttpMessageConverter<>());
        }
        catch (Throwable ex) {
            // Ignore when no TransformerFactory implementation is available...
        }
        messageConverters.add(new AllEncompassingFormHttpMessageConverter());
        if (romePresent) {
            messageConverters.add(new AtomFeedHttpMessageConverter());
            messageConverters.add(new RssChannelHttpMessageConverter());
        }
        if (jackson2XmlPresent) {
            Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.xml();
            if (this.applicationContext != null) {
                builder.applicationContext(this.applicationContext);
            }
            messageConverters.add(new MappingJackson2XmlHttpMessageConverter(builder.build()));
        }
        else if (jaxb2Present) {
            messageConverters.add(new Jaxb2RootElementHttpMessageConverter());
        }
        if (jackson2Present) {
            Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
            if (this.applicationContext != null) {
                builder.applicationContext(this.applicationContext);
            }
            messageConverters.add(new MappingJackson2HttpMessageConverter(builder.build()));
        }
        else if (gsonPresent) {
            messageConverters.add(new GsonHttpMessageConverter());
        }
        else if (jsonbPresent) {
            messageConverters.add(new JsonbHttpMessageConverter());
        }
        if (jackson2SmilePresent) {
            Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.smile();
            if (this.applicationContext != null) {
                builder.applicationContext(this.applicationContext);
            }
            messageConverters.add(new MappingJackson2SmileHttpMessageConverter(builder.build()));
        }
        if (jackson2CborPresent) {
            Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.cbor();
            if (this.applicationContext != null) {
                builder.applicationContext(this.applicationContext);
            }
            messageConverters.add(new MappingJackson2CborHttpMessageConverter(builder.build()));
        }
    }
    // ...
}
```

自定义消息转换器场景

- 浏览器发请求，直接返回 xml，`Accept: application/xml` -> `jacksonXmlConverter`
- 使用 ajax 发起的请求，返回 json，`Accept: application/json` -> `jacksonJsonConverter`
- 使用 app 发起的请求，返回自定义协议数据，`Accept: application/x-guigu` -> `xxxConverter`
  - `属性值1`;`属性值2`

```java
@Controller
@RequestMapping("/response")
public class ResponseController {
    @ResponseBody
    @GetMapping("/person")
    public Person getPerson() {
        Person person = new Person();
        person.setUserName("张三");
        person.setAge(18);
        person.setBirth(new Date());
        Pet pet = new Pet();
        pet.setName("小猫");
        pet.setAge(2);
        person.setPet(pet);
        return person;
    }
}
```

```java
public class GuiguMessageConverter implements HttpMessageConverter<Person> {
    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return false;
    }
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return clazz.isAssignableFrom(Person.class);
    }
    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return MediaType.parseMediaTypes("application/x-guigu");
    }
    @Override
    public Person read(Class<? extends Person> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        return null;
    }
    @Override
    public void write(Person person, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        String data = person.getUserName() + ";" + person.getAge() + ";" + person.getBirth();
        OutputStream body = outputMessage.getBody();
        body.write(data.getBytes(StandardCharsets.UTF_8));
    }
}
```

```java
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig2 {
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.add(new GuiguMessageConverter());
            }
        };
    }
}
```

### 4.5. 自定义参数内容协商

```java
List<MediaType> acceptableTypes = getAcceptableMediaTypes(request);
```

```java
package org.springframework.web.servlet.mvc.method.annotation;
public abstract class AbstractMessageConverterMethodProcessor
        extends AbstractMessageConverterMethodArgumentResolver
        implements HandlerMethodReturnValueHandler {
    // ...
    private final ContentNegotiationManager contentNegotiationManager;
    // ...
    private List<MediaType> getAcceptableMediaTypes(HttpServletRequest request)
            throws HttpMediaTypeNotAcceptableException {
        return this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
    }
    // ...
}
```

- 内容协商策略管理器

```java
package org.springframework.web.accept;
public class ContentNegotiationManager
        implements ContentNegotiationStrategy, MediaTypeFileExtensionResolver {
    // ...
    private final List<ContentNegotiationStrategy> strategies = new ArrayList<>();
    // ...
    public ContentNegotiationManager(ContentNegotiationStrategy... strategies) {
        // 开启参数化内容协商后，加入 ParameterContentNegotiationStrategy
        this(Arrays.asList(strategies));
    }
    public ContentNegotiationManager(Collection<ContentNegotiationStrategy> strategies) {
        Assert.notEmpty(strategies, "At least one ContentNegotiationStrategy is expected");
        this.strategies.addAll(strategies);
        for (ContentNegotiationStrategy strategy : this.strategies) {
            if (strategy instanceof MediaTypeFileExtensionResolver) {
                this.resolvers.add((MediaTypeFileExtensionResolver) strategy);
            }
        }
    }
    public ContentNegotiationManager() {
        // 默认只有 HeaderContentNegotiationStrategy
        this(new HeaderContentNegotiationStrategy());
    }
    // ...
    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest request)
            throws HttpMediaTypeNotAcceptableException {
        for (ContentNegotiationStrategy strategy : this.strategies) {
            List<MediaType> mediaTypes =   strategy.resolveMediaTypes(request);
            if (mediaTypes.equals(MEDIA_TYPE_ALL_LIST)) {
                continue;
            }
            return mediaTypes;
        }
        return MEDIA_TYPE_ALL_LIST;
    }
    // ...
}
```

![内容协商策略](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/内容协商策略.190frsrg9zvk.jpg)

```java
package org.springframework.web.accept;
public class HeaderContentNegotiationStrategy implements ContentNegotiationStrategy {}
```

```java
package org.springframework.web.accept;
public class ParameterContentNegotiationStrategy
        extends AbstractMappingContentNegotiationStrategy {
    // ...
    private String parameterName = "format";
    public ParameterContentNegotiationStrategy(Map<String, MediaType> mediaTypes) {
        super(mediaTypes);
    }
    // ...
}
```

```java
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig2 {
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
                Map<String, MediaType> mediaTypes = new HashMap<>(16);
                mediaTypes.put("json", MediaType.APPLICATION_JSON);
                mediaTypes.put("xml", MediaType.APPLICATION_XML);
                mediaTypes.put("gg", MediaType.parseMediaType("application/x-guigu"));
                ParameterContentNegotiationStrategy parameterStrategy = new ParameterContentNegotiationStrategy(mediaTypes);
                // parameterStrategy.setParameterName("ff");
                HeaderContentNegotiationStrategy headerStrategy = new HeaderContentNegotiationStrategy();
                configurer.strategies(Arrays.asList(parameterStrategy, headerStrategy));
            }
        };
    }
}
```

![自定义内容协商策略](https://cdn.jsdelivr.net/gh/happyflyer/picture-bed@main/2021/自定义内容协商策略.6gllb14835c0.jpg)

## 5. 视图解析与模板引擎

### 5.1. 模板引擎 Thymeleaf

- 表达式
  - 变量取值：`${...}`，获取请求域、session 域、对象等值
  - 选择变量：`*{...}`，获取上下文对象值
  - 消息：`#{...}`，获取国际化等值
  - 链接：`@{...}`，生成链接
  - 片段表达式：`~{...}`，`jsp:include` 作用，引入公共页面片段
- 字面量
  - `'one text'`
  - `'Another one!'`
  - `true`
  - `false`
  - `null`
- 文本操作
  - `'The name is ${name}'`
  - `+`
- 数学运算
- 布尔运算
  - `and`
  - `or`
  - `!`
  - `not`
- 比较运算
  - `>`
  - `<`
  - `>=`
  - `<=`
  - `==`
  - `!=`
- 条件运算
  - `(if)?(then)`
  - `(if)?(then):(else)`
  - `?:(defaultvalue)`
- 特殊操作
  - `_`

```html
<form action="subscribe.html" th:attr="action=@{/subscribe}">
  <fieldset>
    <input type="text" name="email" />
    <input type="submit" value="Subscribe!" th:attr="value=#{subscribe.submit}" />
  </fieldset>
</form>
```

```html
<img src="../../images/gtvglogo.png" th:attr="src=@{/images/gtvglogo.png},title=#{logo},alt=#{logo}" />
```

```html
<input type="submit" value="Subscribe!" th:value="#{subscribe.submit}" />
<form action="subscribe.html" th:action="@{/subscribe}"></form>
```

```html
<tr th:each="prod : ${prods}">
  <td th:text="${prod.name}">Onions</td>
  <td th:text="${prod.price}">2.41</td>
  <td th:text="${prod.inStock} ? #{true} : #{false}">yes</td>
</tr>
```

```html
<tr th:each="prod, iterStat : ${prods}" th:class="${iterStat.odd} ? 'odd'">
  <td th:text="${prod.name}">Onions</td>
  <td th:text="${prod.price}">2.41</td>
  <td th:text="${prod.inStock} ? #{true} : #{false}">yes</td>
</tr>
```

```html
<a href="comments.html" th:href="@{/product/comments(prodId=${prod.id})}" th:if="${not #lists.isEmpty(prod.comments)}"> view </a>
```

```html
<div th:switch="${user.role}">
  <p th:case="'admin'">User is an administrator</p>
  <p th:case="#{roles.manager}">User is a manager</p>
  <p th:case="*">User is some other thing</p>
</div>
```

### 5.2. Thymeleaf 使用

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

```java
package org.springframework.boot.autoconfigure.thymeleaf;
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ThymeleafProperties.class)
@ConditionalOnClass({ TemplateMode.class, SpringTemplateEngine.class })
@AutoConfigureAfter({ WebMvcAutoConfiguration.class, WebFluxAutoConfiguration.class })
public class ThymeleafAutoConfiguration {
    // ...
    private final ThymeleafProperties properties;
    // ...
    @Configuration(proxyBeanMethods = false)
    protected static class ThymeleafDefaultConfiguration {
        @Bean
        @ConditionalOnMissingBean(ISpringTemplateEngine.class)
        SpringTemplateEngine templateEngine(ThymeleafProperties properties,
                                            ObjectProvider<ITemplateResolver> templateResolvers,
                                            ObjectProvider<IDialect> dialects) {
            // ...
        }
    }
    // ...
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    @ConditionalOnProperty(name = "spring.thymeleaf.enabled", matchIfMissing = true)
    static class ThymeleafWebMvcConfiguration {
        // ...
        @Configuration(proxyBeanMethods = false)
        static class ThymeleafViewResolverConfiguration {
            @Bean
            @ConditionalOnMissingBean(name = "thymeleafViewResolver")
            ThymeleafViewResolver thymeleafViewResolver(ThymeleafProperties properties,
                SpringTemplateEngine templateEngine) {
                  // ...
            }
            // ...
        }
        // ...
    }
    // ...
}
```

- 自动配好的策略
  - 所有 thymeleaf 的配置值都在 `ThymeleafProperties`
  - 配置好了 `SpringTemplateEngine`
  - 配好了 `ThymeleafViewResolver`
  - 我们只需要直接开发页面

```java
package org.springframework.boot.autoconfigure.thymeleaf;
@ConfigurationProperties(prefix = "spring.thymeleaf")
public class ThymeleafProperties {
    private static final Charset DEFAULT_ENCODING = StandardCharsets.UTF_8;
    public static final String DEFAULT_PREFIX = "classpath:/templates/";
    public static final String DEFAULT_SUFFIX = ".html";
    // ...
}
```

```java
@Controller
@RequestMapping("/view")
public class ViewController {
    @GetMapping("/success")
    public String success(Model model) {
        model.addAttribute("msg", "你好");
        model.addAttribute("link", "https://cn.bing.com/");
        return "success";
    }
}
```

```html
<!DOCTYPE html>
<html lang="zh" xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <title>success</title>
  </head>
  <body>
    <h1 th:text="${msg}">哈哈</h1>
    <h2>
      <a href="https://www.baidu.com/" th:href="${link}">百度一下，你就知道</a><br />
      <a href="https://www.baidu.com/" th:href="@{link}">百度一下，你就知道</a><br />
      <a href="https://www.baidu.com/" th:href="@{/link}">百度一下，你就知道</a>
    </h2>
  </body>
</html>
```

```yaml
server:
  servlet:
    context-path: /context
```

- [Tutorial: Using Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.0/usingthymeleaf.html)
- [Tutorial: Thymeleaf + Spring](https://www.thymeleaf.org/doc/tutorials/3.0/thymeleafspring.html)
- [Tutorial: Extending Thymeleaf](https://www.thymeleaf.org/doc/tutorials/3.0/extendingthymeleaf.html)

```html
<footer th:fragment="copy">&copy; 2011 The Good Thymes Virtual Grocery</footer>
```

```html
<body>
  <div th:insert="footer :: copy"></div>
  <div th:replace="footer :: copy"></div>
  <div th:include="footer :: copy"></div>
</body>
```

```html
<body>
  <div>
    <footer>&copy; 2011 The Good Thymes Virtual Grocery</footer>
  </div>
  <footer>&copy; 2011 The Good Thymes Virtual Grocery</footer>
  <div>&copy; 2011 The Good Thymes Virtual Grocery</div>
</body>
```

### 5.3. 视图解析器和视图原理

1. 视图解析原理流程
   1. 目标方法处理的过程中，所有数据都会被放在 `ModelAndViewContainer` 里面。包括数据和视图地址
   2. 方法的参数是一个自定义类型对象（从请求参数中确定的），把他重新放在 `ModelAndViewContainer`
   3. 任何目标方法执行完成以后都会返回 `ModelAndView`（数据和视图地址）
   4. `processDispatchResult` 处理派发结果（页面改如何响应）
      1. `render(mv, request, response);` 进行页面渲染逻辑
         1. 根据方法的 String 返回值得到 `View` 对象【定义了页面的渲染逻辑】
            1. 所有的视图解析器尝试是否能根据当前返回值得到 `View` 对象
            2. 得到了 `redirect:/main.html` --> `Thymeleaf new RedirectView()`
            3. `ContentNegotiationViewResolver` 里面包含了下面所有的视图解析器，内部还是利用下面所有视图解析器得到视图对象
            4. `view.render(mv.getModelInternal(), request, response);` 视图对象调用自定义的 `render` 进行页面渲染工作
         2. Redir`ectView 如何渲染【重定向到一个页面】
         3. 获取目标 url 地址
         4. `response.sendRedirect(encodedURL);`

- 视图解析
  - 返回值以 `forward:` 开始
    - `new InternalResourceView(forwardUrl);`
    - `request.getRequestDispatcher(path).forward(request, response);`
  - 返回值以 `redirect:` 开始
    - `new RedirectView()`
    - `render` 就是重定向
  - 返回值是普通字符串
    - `new ThymeleafView()`

### 5.4. 自定义视图解析器和视图
