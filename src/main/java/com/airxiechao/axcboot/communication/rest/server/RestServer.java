package com.airxiechao.axcboot.communication.rest.server;

import com.airxiechao.axcboot.communication.common.Response;
import com.airxiechao.axcboot.communication.common.annotation.Param;
import com.airxiechao.axcboot.communication.common.annotation.Params;
import com.airxiechao.axcboot.communication.rest.annotation.*;
import com.airxiechao.axcboot.communication.rest.aspect.AspectHandler;
import com.airxiechao.axcboot.communication.rest.security.*;
import com.airxiechao.axcboot.communication.rest.util.RestUtil;
import com.alibaba.fastjson.JSON;
import io.undertow.Undertow;
import io.undertow.server.DefaultResponseListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.*;

import static io.undertow.Handlers.*;
import static io.undertow.util.StatusCodes.METHOD_NOT_ALLOWED;
import static io.undertow.util.StatusCodes.NOT_FOUND;

public class RestServer {

    private static final Logger logger = LoggerFactory.getLogger(RestServer.class);
    private static final Logger accessLogger = LoggerFactory.getLogger("access");

    private String ip;
    private int port;
    private AuthRoleChecker authRoleChecker;
    private Undertow server;
    private RoutingHandler router = routing();
    private PathHandler pather = path();

    public RestServer(String ip, int port, AuthRoleChecker authRoleChecker){
        this.ip = ip;
        this.port = port;
        this.authRoleChecker = authRoleChecker;
    }

    public void start(){

        pather.addPrefixPath("/rest", new EagerFormParsingHandler(router));

        server = Undertow.builder()
             .addHttpListener(this.port, this.ip)
             .setHandler(addGzip(addAccessLog(addDefaultError(pather))))
             .build();

        server.start();
    }

    public void stop(){
        server.stop();
    }

    public RestServer registerStatic(String urlPath, String resourcePath,
                                     String welcomeHtml, String loginHtml,
                                     String[] roles){

        ResourceHandler resourceHandler = resource(new PathResourceManager(Path.of(resourcePath)));
        if(null != welcomeHtml){
            resourceHandler.addWelcomeFiles(welcomeHtml);
        }

        if(!urlPath.startsWith("/")){
            urlPath = "/" + urlPath;
        }

        String loginPath = null;
        if(null != loginHtml){
            loginPath = urlPath+(urlPath.endsWith("/")?"":"/")+loginHtml;
        }

        pather.addPrefixPath(
                urlPath,
                addStaticSecurity(resourceHandler, roles, loginPath)
        );

        return this;
    }

    public RestServer registerHandler(Class<?> cls){
        Method[] methods = cls.getDeclaredMethods();
        for(Method method : methods){
            method.setAccessible(true);

            Get get = method.getAnnotation(Get.class);
            if(null != get){
                String path = get.value();
                HttpHandler httpHandler = getMethodHandler(method);
                router.get(path, httpHandler);
            }

            Post post = method.getAnnotation(Post.class);
            if(null != post){
                String path = post.value();
                HttpHandler httpHandler = getMethodHandler(method);
                router.post(path, httpHandler);
            }

            Delete delete = method.getAnnotation(Delete.class);
            if(null != delete){
                String path = delete.value();
                HttpHandler httpHandler = getMethodHandler(method);
                router.delete(path, httpHandler);
            }
        }

        return this;
    }

    private HttpHandler getMethodHandler(Method method){
        HttpHandler httpHandler = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {

                if (httpServerExchange.isInIoThread()) {
                    httpServerExchange.dispatch(this);
                    return;
                }

                try{
                    // check auth
                    checkAuth(method, httpServerExchange);

                    // check guard
                    checkGuard(method, httpServerExchange);

                    // check parameters
                    checkParameter(method, httpServerExchange);

                    // handle aspect before invoke
                    Map<String, Object> aspectParams = new HashMap<>();
                    handleAspectBeforeInvoke(method, aspectParams);

                    int methodParamCount = method.getParameterCount();;
                    String queryPath = httpServerExchange.getRequestPath();
                    if(queryPath.endsWith("download") ||
                            queryPath.endsWith("notify")){
                        httpServerExchange.startBlocking();
                        if(1 == methodParamCount){
                            method.invoke(null, httpServerExchange);
                        }else if(2 == methodParamCount){
                            method.invoke(null, httpServerExchange, aspectParams);
                        }else{
                            throw new Exception("rest method parameter count error");
                        }

                    }else{
                        Object ret = null;
                        if(1 == methodParamCount){
                            ret = method.invoke(null, httpServerExchange);
                        }else if(2 == methodParamCount){
                            ret = method.invoke(null, httpServerExchange, aspectParams);
                        }else{
                            throw new Exception("rest method parameter count error");
                        }

                        httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                        httpServerExchange.getResponseSender().send(JSON.toJSONString(ret));
                    }

                    // handle aspect after invoke
                    handleAspectAfterInvoke(method, aspectParams);
                }catch (Exception e){

                    logger.error("rest handler error", e);

                    Response resp = new Response();

                    if(e instanceof AuthException){
                        resp.authError(e.getMessage());
                    }else{
                        String errMessage = e.getMessage();
                        if(null == errMessage || errMessage.isBlank()){
                            errMessage = e.getCause().getMessage();
                        }
                        resp.error(errMessage);
                    }

                    httpServerExchange.getResponseHeaders().clear();
                    httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

                    httpServerExchange.getResponseSender().send(JSON.toJSONString(resp));
                }

            }
        };

        return httpHandler;
    }

    private void checkAuth(Method method, HttpServerExchange httpServerExchange) throws AuthException{
        Auth auth = method.getAnnotation(Auth.class);
        if(null == auth){
            auth = method.getDeclaringClass().getAnnotation(Auth.class);
        }

        if(null != auth){
            // if ignore
            if(auth.ignore()){
                return;
            }

            // require check auth
            String[] roles = auth.roles();
            checkAuthToken(httpServerExchange, roles);
        }
    }

    private void checkAuthToken(HttpServerExchange httpServerExchange, String[] roles) throws AuthException {

        AuthPrincipal authPrincipal = RestUtil.getAuthPrincipal(httpServerExchange);
        if(null == authPrincipal){
            throw new AuthException("invalid auth token");
        }

        Date now = new Date();
        if(authPrincipal.getExpireTime().before(now)){
            throw new AuthException("auth token expired");
        }

        boolean hasRole = authRoleChecker.hasRole(authPrincipal, roles);
        if(!hasRole){
            throw new AuthException("no user or mis-match role");
        }
    }

    private void checkGuard(Method method, HttpServerExchange httpServerExchange) throws Exception{
        Guard guard = method.getAnnotation(Guard.class);
        if(null == guard){
            guard = method.getDeclaringClass().getAnnotation(Guard.class);
        }

        if(null != guard){
            // if ignore
            if(guard.ignore()){
                return;
            }

            // require check guard
            checkGuardToken(method, httpServerExchange);
        }
    }

    private void checkGuardToken(Method method, HttpServerExchange httpServerExchange) throws Exception {

        GuardPrincipal guardPrincipal = RestUtil.getGuardPrincipal(httpServerExchange);
        if(null == guardPrincipal){
            throw new Exception("invalid guard token");
        }

        Date now = new Date();
        if(guardPrincipal.getExpireTime().before(now)){
            throw new Exception("guard token expired");
        }

        String restPath = RestUtil.getRestPath(method);
        if(guardPrincipal.getPath().equals(restPath)){
            throw new Exception("mis-match method");
        }
    }

    private void checkParameter(Method method, HttpServerExchange httpServerExchange) throws Exception {
        Annotation[] methodAnnos = method.getAnnotations();
        List<Param> params = new ArrayList<>();
        for(Annotation anno : methodAnnos){
            if(anno instanceof Params){
                params.addAll(Arrays.asList(((Params) anno).value()));
            }else if(anno instanceof Param){
                Param param = (Param)anno;
                params.add(param);
            }
        }

        Map<String, Deque<String>> queryParam = httpServerExchange.getQueryParameters();
        FormData formData = httpServerExchange.getAttachment(FormDataParser.FORM_DATA);

        for(Param param : params){
            String name = param.value();
            boolean required = param.required();


            if(required){
                Deque<String> dv = queryParam.get(name);
                boolean queryExisted = null != dv && null != dv.getFirst() && !dv.getFirst().isBlank();
                boolean formExisted = false;
                if(null != formData && null != formData.get(name) &&
                        null != formData.get(name).getFirst()){
                    if(formData.get(name).getFirst().isFileItem()){
                        // is file
                        if(null != formData.get(name).getFirst().getFileItem()){
                            formExisted = true;
                        }
                    }else{
                        // not file
                        if(null != formData.get(name).getFirst().getValue() &&
                                !formData.get(name).getFirst().getValue().isBlank()){
                            formExisted = true;
                        }
                    }
                }

                if(!queryExisted && !formExisted){
                    throw new Exception("parameter [" + name + "] is required");
                }
            }
        }
    }

    private void handleAspectBeforeInvoke(Method method, Map aspectParams){
        Annotation[] methodAnnos = method.getAnnotations();
        List<PinToRest> pins = new ArrayList<>();
        for(Annotation anno : methodAnnos){
            if(anno instanceof PinToRests){
                pins.addAll(Arrays.asList(((PinToRests) anno).value()));
            }else if(anno instanceof PinToRest){
                PinToRest pin = (PinToRest)anno;
                pins.add(pin);
            }
        }

        for(PinToRest pin : pins){
            if(pin.when().equals(PinToRestWhen.BEFORE_INVOKE)){
                String handlerDesc = pin.desc();
                Class handlerCls = pin.handler();
                try {
                    AspectHandler handler = (AspectHandler)handlerCls.getConstructor().newInstance();
                    handler.handle(aspectParams);
                } catch (Exception e) {
                    logger.error("handle rest aspect before invoke [{}] error", handlerDesc, e);
                }
            }
        }
    }

    private void handleAspectAfterInvoke(Method method, Map aspectParams){
        Annotation[] methodAnnos = method.getAnnotations();
        List<PinToRest> pins = new ArrayList<>();
        for(Annotation anno : methodAnnos){
            if(anno instanceof PinToRests){
                pins.addAll(Arrays.asList(((PinToRests) anno).value()));
            }else if(anno instanceof PinToRest){
                PinToRest pin = (PinToRest)anno;
                pins.add(pin);
            }
        }

        for(PinToRest pin : pins){
            if(pin.when().equals(PinToRestWhen.AFTER_INVOKE)){
                String handlerDesc = pin.desc();
                Class handlerCls = pin.handler();
                try {
                    AspectHandler handler = (AspectHandler)handlerCls.getConstructor().newInstance();
                    handler.handle(aspectParams);
                } catch (Exception e) {
                    logger.error("handle rest aspect after invoke [{}] error", handlerDesc, e);
                }
            }
        }
    }

    private HttpHandler addStaticSecurity(final HttpHandler toWrap, String[] roles, String loginPath){
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
                String path = httpServerExchange.getRequestPath();
                String query = httpServerExchange.getQueryString();
                String fullPaht = path + "?" + query;

                boolean ignore =
                        path.endsWith(".js") ||
                        path.endsWith(".css") ||
                        path.endsWith(".jpg") ||
                        path.indexOf("/img/") >= 0 ||
                        path.equals(loginPath);

                if(!ignore && null != roles && roles.length > 0){
                    try{
                        checkAuthToken(httpServerExchange, roles);
                    }catch (AuthException e){
                        if(null != loginPath){
                            RestUtil.redirect(httpServerExchange, loginPath+"?redirect="+ URLEncoder.encode(fullPaht, "UTF-8"));
                            return;
                        }

                        throw e;
                    }

                }

                toWrap.handleRequest(httpServerExchange);
            }
        };
    }

    private HttpHandler addAccessLog(final HttpHandler toWrap){
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
                String path = httpServerExchange.getRequestPath();
                String remoteIp = RestUtil.getRemoteIp(httpServerExchange);

                if(path.startsWith("/rest/")){
                    accessLogger.info(remoteIp + " => " + path);
                }

                toWrap.handleRequest(httpServerExchange);
            }
        };
    }

    private HttpHandler addGzip(final HttpHandler toWrap){
        HttpHandler encodingHandler = new EncodingHandler.Builder().build(null)
                .wrap(toWrap);
        return encodingHandler;
    }

    private HttpHandler addDefaultError(final HttpHandler toWrap){
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {

                httpServerExchange.addDefaultResponseListener(new DefaultResponseListener() {
                    @Override
                    public boolean handleDefaultResponse(final HttpServerExchange exchange) {
                        if (!exchange.isResponseChannelAvailable()) {
                            return false;
                        }

                        int statusCode = exchange.getStatusCode();
                        if(NOT_FOUND != statusCode && METHOD_NOT_ALLOWED != statusCode){
                            return false;
                        }

                        httpServerExchange.getResponseHeaders().clear();
                        httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

                        Response resp = new Response();
                        resp.error("not found");

                        httpServerExchange.getResponseSender().send(JSON.toJSONString(resp));
                        return true;
                    }
                });

                toWrap.handleRequest(httpServerExchange);
            }
        };
    }
}
