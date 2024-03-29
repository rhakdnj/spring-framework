package com.exam;

import com.exam.annotation.*;
import com.exam.mymap.MyMap;
import com.exam.util.Util;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ControllerManager {
    private static final Map<String, RouteInfo> routeInfos = new HashMap<>();

    static {
        scanMappings();
    }

    private static void scanMappings() {
        Reflections reflections = new Reflections(App.BASE_PACKAGE_PATH);
        for (Class<?> controllerCls : reflections.getTypesAnnotatedWith(Controller.class)) {
            Method[] methods = controllerCls.getDeclaredMethods();

            for (Method method : methods) {
                GetMapping getMapping = method.getAnnotation(GetMapping.class);
                PostMapping postMapping = method.getAnnotation(PostMapping.class);
                PutMapping putMapping = method.getAnnotation(PutMapping.class);
                DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);

                String httpMethod = null;
                String path = null;

                if (getMapping != null) {
                    path = getMapping.value();
                    httpMethod = "GET";
                } else if (postMapping != null) {
                    path = postMapping.value();
                    httpMethod = "POST";
                } else if (putMapping != null) {
                    path = putMapping.value();
                    httpMethod = "POST";
                } else if (deleteMapping != null) {
                    path = deleteMapping.value();
                    httpMethod = "DELETE";
                }

                if (path != null && httpMethod != null) {
                    String actionPath = Util.str.beforeFrom(path, "/", 4);

                    String key = httpMethod + "___" + actionPath;

                    routeInfos.put(key, new RouteInfo(path, actionPath, controllerCls, method));
                }
            }
        }
    }

    public static void runAction(HttpServletRequest req, HttpServletResponse resp) {
        Rq rq = new Rq(req, resp);

        String routeMethod = rq.getRouteMethod();
        String actionPath = rq.getActionPath();

        String mappingKey = routeMethod + "___" + actionPath;


        boolean contains = routeInfos.containsKey(mappingKey);

        if (!contains) {
            rq.writeln("해당 요청은 존재하지 않습니다.");
            return;
        }

        RouteInfo routeInfo = routeInfos.get(mappingKey);
        rq.setRouteInfo(routeInfo);

        runAction(rq);
    }

    private static void runAction(Rq rq) {
        RouteInfo routeInfo = rq.getRouteInfo();
        Class controllerCls = routeInfo.getControllerCls();
        Method actionMethod = routeInfo.getMethod();
        Object controllerObj = Container.getObj(controllerCls);

        try {
            actionMethod.invoke(controllerObj, rq);
        } catch (IllegalAccessException e) {
            rq.writeln("액션시작에 실패하였습니다.");
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } finally {
            MyMap myMap = Container.getObj(MyMap.class);
            myMap.closeConnection();
            // 현재 쓰레드에 할당된 커넥션을 닫는다.
            // 커넥션을 닫지 않으면 ->  매 요청마다, DB 요청이 쌓인다.
        }
    }

    public static void init() {

    }

    public static Map<String, RouteInfo> getRouteInfosForTest() {
        return routeInfos;
    }
}
