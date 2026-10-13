package dev.claude.assistant.remote;
interface IPrivilegedDeviceService {
    String execute(String command, int timeoutSeconds) = 0;
    void cancel() = 1;
    int uid() = 2;
    void destroy() = 16777114;
}
