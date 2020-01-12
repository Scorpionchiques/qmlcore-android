package com.pureqml.android.runtime;

import android.util.Log;

import com.eclipsesource.v8.JavaCallback;
import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Function;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import com.pureqml.android.IExecutionEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class Wrapper {
    public final static String TAG = "ClassWrapper";

    public static final Object getValue(IExecutionEnvironment env, Class<?> type, Object object) {
        if (type != null && object.getClass() == type) {
            return object;
        } else if (object instanceof V8Value) {
            V8Value value = (V8Value)object;
            switch(value.getV8Type()) {
                case V8Value.UNDEFINED:
                case V8Value.NULL:
                    value.close();
                    return null;
                case V8Value.V8_OBJECT:
                    if (type != V8Object.class && type != V8Function.class) {
                        Object element = env.getObjectById(value.hashCode());
                        value.close();
                        return element;
                    } else {
                        value.close();
                        return object;
                    }
                default:
                    value.close();
                    throw new Error("can't convert value of type " + value.getClass());
            }
        } else
            return object;
    }

    public static final class MethodWrapper implements JavaCallback {
        IExecutionEnvironment _env;
        private final Method _method;
        public MethodWrapper(IExecutionEnvironment env, Method method) { _env = env; _method = method; }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            BaseObject element = _env.getObjectById(self.hashCode());
            int n = arguments.length();
            Class<?> argsType [] = _method.getParameterTypes();
            Object [] targetArguments = new Object[n];
            for(int i = 0; i < n; ++i) {
                targetArguments[i] = getValue(_env, argsType[i], arguments.get(i));
            }
            try {
                return _method.invoke(element, targetArguments);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "MethodWrapper: IllegalAccessException", e);
                throw new RuntimeException("invoke failed: " + e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "MethodWrapper: InvocationTargetException", e);
                throw new RuntimeException("invoke failed: " + e.getTargetException().toString());
            }
        }
    }

    public static final class SimpleMethodWrapper implements JavaCallback {
        IExecutionEnvironment _env;
        private final Method _method;

        public SimpleMethodWrapper(IExecutionEnvironment env, Method method) { _env = env; _method = method; }

        @Override
        public Object invoke(V8Object self, V8Array arguments) {
            BaseObject object = _env.getObjectById(self.hashCode());
            try {
                return _method.invoke(object, arguments);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "SimpleMethodWrapper: IllegalAccessException", e);
                throw new RuntimeException("invoke failed: " + e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "SimpleMethodWrapper: InvocationTargetException", e);
                throw new RuntimeException("invoke failed: " + e.getTargetException().toString());
            }
        }
    }

    public static void generatePrototype(IExecutionEnvironment env, V8 v8, V8Object prototype, Class<?> cls) {
        Log.i(TAG, "wrapping class " + cls.getName());

        for(Method method : cls.getMethods()) {
            String name = method.getName();
            int mods = method.getModifiers();
            //for some reasons android always return public (1) here :\
            if (!method.getDeclaringClass().equals(cls) || Modifier.isPrivate(mods) || Modifier.isProtected(mods) || method.isSynthetic())
                continue;

            Class<?>[] argTypes = method.getParameterTypes();
            Log.i(TAG, "wrapping method " + name + " with " + argTypes.length + " args");
            if (argTypes.length == 1 && argTypes[0].equals(V8Array.class)) {
                prototype.registerJavaMethod(new SimpleMethodWrapper(env, method), name);
            } else
                prototype.registerJavaMethod(new MethodWrapper(env, method), name);
        }
    }

    private static final class ConstructorWrapper implements JavaVoidCallback {
        IExecutionEnvironment   _env;
        Constructor<?>          _ctor;

        public ConstructorWrapper(IExecutionEnvironment env, Constructor<?> ctor) {
            _env = env;
            _ctor = ctor;
        }

        @Override
        public void invoke(V8Object self, V8Array arguments) {
            Class<?> ctorArgs[] = _ctor.getParameterTypes();
            int n = ctorArgs.length;
            Object args[] = new Object[n];
            args[0] = _env;
            for(int i = 1; i < n; ++i) {
                args[i] = getValue(_env, ctorArgs[i], arguments.get(i - 1));
            }
            try {
                Object obj = _ctor.newInstance(args);
                _env.putObject(self.hashCode(), (BaseObject) obj);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public static V8Object generateClass(IExecutionEnvironment env, V8 v8, V8Object namespace, String className, Class<?> cls, Class<?> ctorArgs[]) {
        try {
            Constructor<?> ctor = cls.getConstructor(ctorArgs);
            Log.i(TAG, "wrapping ctor of " + cls.getName());
            namespace.registerJavaMethod(new ConstructorWrapper(env, ctor), className);

            V8Object function = namespace.getObject(className);
            V8Object prototype = function.getObject("prototype");

            generatePrototype(env, v8, prototype, cls);

            function.close();

            return prototype;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
