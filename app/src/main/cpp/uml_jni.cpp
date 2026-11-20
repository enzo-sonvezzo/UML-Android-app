#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <fcntl.h>
#include <pty.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <cerrno>
#include <cstring>
#include <cstdio>
#include <android/log.h>

#define TAG "UML_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jintArray JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeOpenPty(JNIEnv *env, jobject thiz, jint rows, jint cols) {
    int master_fd = -1;
    int slave_fd = -1;
    struct winsize ws;
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    if (openpty(&master_fd, &slave_fd, nullptr, nullptr, &ws) < 0) {
        LOGE("Failed to allocate pseudoterminal via openpty: %s", strerror(errno));
        return nullptr;
    }

    // Set non-blocking on master
    int flags = fcntl(master_fd, F_GETFL, 0);
    fcntl(master_fd, F_SETFL, flags | O_NONBLOCK);

    jintArray result = env->NewIntArray(2);
    jint fds[2] = {master_fd, slave_fd};
    env->SetIntArrayRegion(result, 0, 2, fds);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeForkAndExec(
        JNIEnv *env,
        jobject thiz,
        jobjectArray cmd_array,
        jint slave_fd,
        jstring working_dir
) {
    int count = env->GetArrayLength(cmd_array);
    std::vector<std::string> args_str;
    std::vector<char*> args;

    for (int i = 0; i < count; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(cmd_array, i);
        const char *chars = env->GetStringUTFChars(jstr, nullptr);
        args_str.emplace_back(chars);
        env->ReleaseStringUTFChars(jstr, chars);
    }

    for (auto &str : args_str) {
        args.push_back(const_cast<char*>(str.c_str()));
    }
    args.push_back(nullptr);

    const char *cwd = env->GetStringUTFChars(working_dir, nullptr);
    std::string cwd_str = cwd;
    env->ReleaseStringUTFChars(working_dir, cwd);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("Fork failed: %s", strerror(errno));
        return -1;
    }

    if (pid == 0) {
        // Child process
        setsid();
        ioctl(slave_fd, TIOCSCTTY, 0);

        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);

        if (slave_fd > 2) {
            close(slave_fd);
        }

        // UML requires a writable directory for its mmap RAM backing file
        setenv("TMPDIR", cwd_str.c_str(), 1);
        setenv("HOME", cwd_str.c_str(), 1);
        setenv("TERM", "xterm-256color", 1);
        setenv("DEBIAN_FRONTEND", "noninteractive", 1);

        if (chdir(cwd_str.c_str()) != 0) {
            dprintf(STDERR_FILENO, "chdir(%s) failed: %s\r\n", cwd_str.c_str(), strerror(errno));
        }

        execvp(args[0], args.data());

        // Print exact error to PTY so it displays in TerminalScreen
        dprintf(STDERR_FILENO, "execvp(%s) failed: %s (errno=%d)\r\n", args[0], strerror(errno), errno);
        LOGE("execvp failed for %s: %s (errno=%d)", args[0], strerror(errno), errno);
        _exit(127);
    }

    // Parent process closes slave descriptor
    close(slave_fd);
    LOGI("Spawned UML process pid: %d", pid);
    return pid;
}

JNIEXPORT void JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeSetWindowSize(
        JNIEnv *env,
        jobject thiz,
        jint master_fd,
        jint rows,
        jint cols
) {
    struct winsize ws;
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(master_fd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeWrite(
        JNIEnv *env,
        jobject thiz,
        jint master_fd,
        jbyteArray data,
        jint offset,
        jint length
) {
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    ssize_t written = write(master_fd, bytes + offset, length);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (jint) written;
}

JNIEXPORT jint JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeRead(
        JNIEnv *env,
        jobject thiz,
        jint master_fd,
        jbyteArray buffer,
        jint offset,
        jint length
) {
    jbyte *bytes = env->GetByteArrayElements(buffer, nullptr);
    ssize_t bytes_read = read(master_fd, bytes + offset, length);
    env->ReleaseByteArrayElements(buffer, bytes, 0);
    return (jint) bytes_read;
}

JNIEXPORT void JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeKill(JNIEnv *env, jobject thiz, jint pid, jint sig) {
    if (pid > 0) {
        kill(pid, sig);
    }
}

JNIEXPORT jint JNICALL
Java_com_agy_uml_service_UmlProcessBridge_nativeWaitPid(JNIEnv *env, jobject thiz, jint pid, jboolean nohang) {
    int status = 0;
    int options = nohang ? WNOHANG : 0;
    pid_t res = waitpid(pid, &status, options);
    if (res > 0) {
        if (WIFEXITED(status)) return WEXITSTATUS(status);
        if (WIFSIGNALED(status)) return -WTERMSIG(status);
    }
    return res;
}

} // extern "C"
