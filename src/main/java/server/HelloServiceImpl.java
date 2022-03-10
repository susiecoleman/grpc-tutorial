package server;

import static io.grpc.Status.INVALID_ARGUMENT;

import com.example.grpc.ErrorResponse;
import com.example.grpc.HelloRequest;
import com.example.grpc.HelloResponse;
import com.example.grpc.HelloResponseSummary;
import com.example.grpc.HelloServiceGrpc.HelloServiceImplBase;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class HelloServiceImpl extends HelloServiceImplBase {

  @Override
  public void hello(
      final HelloRequest request, final StreamObserver<HelloResponse> responseObserver) {

    if (request.getFirstName().isEmpty()) {
      final var key = ProtoUtils.keyForProto(ErrorResponse.getDefaultInstance());
      final var errorResponse = ErrorResponse.newBuilder().setError("No first name").build();
      final var metadata = new Metadata();
      metadata.put(key, errorResponse);
      responseObserver.onError(
          INVALID_ARGUMENT
              .withDescription("Did not provide first name")
              .asRuntimeException(metadata));
    }

    if (request.getLastName().isEmpty()) {
      final var response =
          Status.newBuilder()
              .setCode(Code.INVALID_ARGUMENT.getNumber())
              .setMessage("Missing last name")
              .addDetails(Any.pack(ErrorInfo.newBuilder().setReason("Last name missing").build()))
              .build();
      responseObserver.onError(StatusProto.toStatusRuntimeException(response));
    }

    final var greeting = buildGreeting("Hello", request.getFirstName(), request.getLastName());
    final var response = HelloResponse.newBuilder().setGreeting(greeting).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void helloServerStream(
      final HelloRequest request, final StreamObserver<HelloResponse> responseObserver) {
    final var firstName = request.getFirstName();
    final var lastName = request.getLastName();

    Stream.of("hello", "hola", "bonjour", "guten tag")
        .forEach(
            greeting -> {
              final var response =
                  HelloResponse.newBuilder()
                      .setGreeting(buildGreeting(greeting, firstName, lastName))
                      .build();
              responseObserver.onNext(response);
            });
    responseObserver.onCompleted();
  }

  @Override
  public StreamObserver<HelloRequest> helloClientStream(
      final StreamObserver<HelloResponseSummary> responseObserver) {
    return new StreamObserver<HelloRequest>() {
      final List<String> peopleWhoSaidHello = new ArrayList<>();

      @Override
      public void onNext(final HelloRequest helloRequest) {
        String person = helloRequest.getFirstName() + " " + helloRequest.getLastName();
        peopleWhoSaidHello.add(person);
      }

      @Override
      public void onError(final Throwable throwable) {
        System.out.println("helloClientStream failed");
      }

      @Override
      public void onCompleted() {
        final var res =
            HelloResponseSummary.newBuilder().addAllGreeting(peopleWhoSaidHello).build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();
      }
    };
  }

  @Override
  public StreamObserver<HelloRequest> helloStream(
      final StreamObserver<HelloResponse> responseObserver) {
    return new StreamObserver<HelloRequest>() {
      private final List<String> greetings = List.of("hello", "hola", "bonjour", "guten tag");
      private final Random random = new Random();

      @Override
      public void onNext(final HelloRequest helloRequest) {
        final var res =
            HelloResponse.newBuilder()
                .setGreeting(
                    buildGreeting(
                        greetings.get(random.nextInt(greetings.size())),
                        helloRequest.getFirstName(),
                        helloRequest.getLastName()))
                .build();
        responseObserver.onNext(res);
      }

      @Override
      public void onError(final Throwable throwable) {
        System.out.println("helloStream Failed");
      }

      @Override
      public void onCompleted() {
        responseObserver.onCompleted();
      }
    };
  }

  private String buildGreeting(String greeting, String firstName, String lastName) {
    return greeting + " " + firstName + " " + lastName;
  }
}
