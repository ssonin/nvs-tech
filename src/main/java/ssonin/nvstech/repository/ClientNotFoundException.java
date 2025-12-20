package ssonin.nvstech.repository;

final class ClientNotFoundException extends NotFoundException {

  ClientNotFoundException() {
    super("Client not found");
  }
}
