Simple Java utility to share files in a network. This is useful when there is no common network share accessible from two machines. Also this can bypass any security software installed on corporate networks which restricts file sharing.

This uses TCP connection. TCP port number used in server is configurable. This is light weight and do not depend on any third party library.

It is equivalent to P2P(Peer to Peer) but with limitation of it works between only two machines.

There are 2 classes. SocketUploadServer acts as server and SocketUploadClient acts as client. All configuration including file path for uploading and saving file can be configured in console input.