package client;

import com.example.grpc.ErrorResponse;
import com.example.grpc.HelloRequest;
import com.example.grpc.HelloResponse;
import com.example.grpc.HelloResponseSummary;
import com.example.grpc.HelloServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class GrpcClient {
  public static void main(String[] args) {
    final var channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();

    final var blockingStub = HelloServiceGrpc.newBlockingStub(channel);
    final var asyncStub = HelloServiceGrpc.newStub(channel);

    //    callHello(blockingStub);
//    callHelloAndTriggerExceptionNoFirstName(blockingStub);
    callHelloAndTriggerExceptionNoLastName(blockingStub);
    //    callHelloServerStream(blockingStub);
    //    callHelloClientStream(asyncStub);
    //    callHelloStream(asyncStub);
    channel.shutdown();
  }

  private static void callHello(HelloServiceGrpc.HelloServiceBlockingStub blockingStub) {
    final var helloRequest =
        HelloRequest.newBuilder().setFirstName("first").setLastName("last").build();
    final var helloResponse = blockingStub.hello(helloRequest);
    System.out.println(helloResponse.getGreeting());
  }

  private static void callHelloAndTriggerExceptionNoFirstName(
      HelloServiceGrpc.HelloServiceBlockingStub blockingStub) {
    try {
      blockingStub.hello(HelloRequest.newBuilder().build());
    } catch (StatusRuntimeException e) {
      System.out.println(e);
      Metadata metadata = Status.trailersFromThrowable(e);
      System.out.println(
          metadata != null
              ? metadata.get(ProtoUtils.keyForProto(ErrorResponse.getDefaultInstance())).getError()
              : null);
    }
  }

  private static void callHelloAndTriggerExceptionNoLastName(
      HelloServiceGrpc.HelloServiceBlockingStub blockingStub) {
    try {
      blockingStub.hello(HelloRequest.newBuilder().setFirstName("first").build());
    } catch (StatusRuntimeException e) {
      System.out.println(e);
      final var status = StatusProto.fromThrowable(e);
      System.out.println(status != null ? status.getCode() : 0);
      System.out.println(status.getMessage());
      System.out.println(status.getDetailsList());
    }
  }

  private static void callHelloServerStream(
      HelloServiceGrpc.HelloServiceBlockingStub blockingStub) {
    final var helloServerStreamRequest =
        HelloRequest.newBuilder().setFirstName("first").setLastName("last").build();
    final var helloServerStreamResponse = blockingStub.helloServerStream(helloServerStreamRequest);
    helloServerStreamResponse.forEachRemaining(System.out::println);
  }

  private static void callHelloClientStream(HelloServiceGrpc.HelloServiceStub stub) {
    final var finishLatch = new CountDownLatch(1);
    final var responseObserver =
        new StreamObserver<HelloResponseSummary>() {
          @Override
          public void onNext(final HelloResponseSummary helloResponseSummary) {
            helloResponseSummary.getGreetingList().forEach(System.out::println);
          }

          @Override
          public void onError(final Throwable throwable) {
            System.out.println("callHelloClientStream failed");
            finishLatch.countDown();
          }

          @Override
          public void onCompleted() {
            System.out.println("callHelloClientStream completed");
            finishLatch.countDown();
          }
        };
    final var requestObserver = stub.helloClientStream(responseObserver);
    Stream.of("A", "B", "C", "D")
        .forEach(
            val -> {
              requestObserver.onNext(
                  HelloRequest.newBuilder().setFirstName("person").setLastName(val).build());
              try {
                Thread.sleep(500);
              } catch (InterruptedException e) {
                System.out.println("Thread interrupted in callHelloClientStream");
                requestObserver.onError(e);
              }
            });
    requestObserver.onCompleted();
    try {
      finishLatch.await(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void callHelloStream(HelloServiceGrpc.HelloServiceStub stub) {
    final var finishLatch = new CountDownLatch(1);
    final var responseObserver =
        new StreamObserver<HelloResponse>() {
          @Override
          public void onNext(final HelloResponse helloResponse) {
            System.out.println(helloResponse.getGreeting());
          }

          @Override
          public void onError(final Throwable throwable) {
            System.out.println("Error in response observer");
          }

          @Override
          public void onCompleted() {
            System.out.println("Finished");
          }
        };

    StreamObserver<HelloRequest> requestObserver = stub.helloStream(responseObserver);
    Stream.of("A", "B", "C", "D")
        .forEach(
            val -> {
              final var request =
                  HelloRequest.newBuilder().setFirstName("Person").setLastName(val).build();
              requestObserver.onNext(request);
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });
    requestObserver.onCompleted();
    try {
      finishLatch.await(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
