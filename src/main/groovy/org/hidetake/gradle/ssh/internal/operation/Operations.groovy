package org.hidetake.gradle.ssh.internal.operation

import org.hidetake.gradle.ssh.plugin.OperationSettings
import org.hidetake.gradle.ssh.plugin.Remote

/**
 * Interface of operations.
 *
 * @author hidetake.org
 */
interface Operations {
    Remote getRemote()

    void shell(OperationSettings settings)

    String execute(OperationSettings settings, String command, Closure callback)

    void executeBackground(OperationSettings settings, String command, Closure callback)

    /**
     * Perform SFTP operations.
     *
     * @param closure closure for {@link SftpHandler}
     */
    void sftp(Closure closure)
}
