

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * Created by jiaoguangcai on 2017/4/21.
 */

public class MainStart {
    //    同步工具
    private static CountDownLatch countDownLatch = new CountDownLatch(1);
    private final static int port = 9999;
    private final static String host = "127.0.0.1";

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        //    生产者线程
        new Thread(new Provider(new DemoServiceImpl(), port)).start();
        //    等待生产者启动
        countDownLatch.await();
        //    生产者线程
        Consumer<DemoService> consumer = new Consumer<DemoService>(DemoService.class, host, port);
        //    新建一个future任务
        FutureTask future = new FutureTask<DemoService>(consumer);
        //    启动消费者
        new Thread(future).start();
        DemoService demoService = (DemoService) future.get();
        for (int i = 1; ; i++) {
            System.out.println(demoService.sayHello(i));
            Thread.sleep(1000);
        }
    }


    interface DemoService {
        String sayHello(int i);
    }

    static class DemoServiceImpl implements DemoService {
        @Override
        public String sayHello(int i) {
            return "你好" + i;
        }
    }

    static class Provider implements Runnable {
        private Object service;
        private int port;
        private ExecutorService executorService = Executors.newCachedThreadPool();

        public Provider(Object service, int port) {
            this.service = service;
            this.port = port;
        }

        @Override
        public void run() {
            if (null == service) {
                throw new IllegalArgumentException("服务提供者为null");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("端口不合法");
            }
            try {
                final ServerSocket serverSocket = new ServerSocket(port);
                countDownLatch.countDown();
                System.out.println("生产者已就绪。。。");
                while (true) {
                    final Socket socket = serverSocket.accept();
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            ObjectInputStream inputStream = null;
                            ObjectOutputStream outputStream = null;
                            try {
                                inputStream = new ObjectInputStream(socket.getInputStream());
                                String methodName = inputStream.readUTF();
                                Class<?>[] parameterType = (Class<?>[]) inputStream.readObject();
                                Object[] parameterValues = (Object[]) inputStream.readObject();
                                Method method = service.getClass().getMethod(methodName, parameterType);
                                Object o = method.invoke(service, parameterValues);
                                outputStream = new ObjectOutputStream(socket.getOutputStream());
                                outputStream.writeObject(o);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            } catch (NoSuchMethodException e) {
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            } finally {
                                try {
                                    if (inputStream != null) {
                                        inputStream.close();
                                    }
                                    if (outputStream != null) {
                                        outputStream.close();
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    };
                    executorService.submit(runnable);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("开启服务失败");
            }
        }
    }

    static class Consumer<T> implements Callable {
        private Class<?> server;
        private String host;
        private int port;

        public Consumer(Class<?> server, String host, int port) {
            this.server = server;
            this.host = host;
            this.port = port;
        }

        @Override
        public T call() throws Exception {
            if (server == null) {
                throw new IllegalArgumentException("接口错误");
            }
            if (null == host) {
                throw new IllegalArgumentException("机器ip错误");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("端口不合法");
            }
            return (T) Proxy.newProxyInstance(server.getClassLoader(), new Class[]{server}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    Socket socket = new Socket(host, port);
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                    outputStream.writeUTF(method.getName());
                    outputStream.writeObject(method.getParameterTypes());
                    outputStream.writeObject(args);
                    ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                    Object o = inputStream.readObject();
                    return o;
                }
            });
        }
    }
}