package org.hidetake.groovy.ssh.test.server

import org.apache.sshd.SshServer
import org.apache.sshd.server.PasswordAuthenticator
import org.hidetake.groovy.ssh.Ssh
import org.hidetake.groovy.ssh.core.Service
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.mop.Use

import static org.hidetake.groovy.ssh.test.server.FileDivCategory.DirectoryType.DIRECTORY
import static org.hidetake.groovy.ssh.test.server.FilenameUtils.toUnixSeparator
import static org.hidetake.groovy.ssh.test.server.Helper.uuidgen

@Use(FileDivCategory)
abstract class AbstractFileTransferSpecification extends Specification {

    @Shared
    SshServer server

    Service ssh

    @Rule
    TemporaryFolder temporaryFolder=new TemporaryFolder(new File("build"))

    def setupSpec() {
        server = SshServerMock.setUpLocalhostServer()
        server.passwordAuthenticator = Mock(PasswordAuthenticator) {
            (1.._) * authenticate('someuser', 'somepassword', _) >> true
        }
    }

    def cleanupSpec() {
        server.stop(true)
    }


    def setup() {
        ssh = Ssh.newService()
        ssh.settings {
            knownHosts = allowAnyHosts
        }
        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                password = 'somepassword'
            }
        }
    }


    def "put() should accept a path string"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: sourceFile.path, into: toUnixSeparator(destinationFile.path)
            }
        }

        then:
        sourceFile.text == text
        destinationFile.text == text
    }

    def "put() should accept a File object"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: sourceFile, into: toUnixSeparator(destinationFile.path)
            }
        }

        then:
        sourceFile.text == text
        destinationFile.text == text
    }

    def "put() should accept a collection of file"() {
        given:
        def sourceDir = temporaryFolder.newFolder()
        def sourceFile1 = sourceDir / uuidgen() << uuidgen()
        def sourceFile2 = sourceDir / uuidgen() << uuidgen()
        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: [sourceFile1, sourceFile2], into: toUnixSeparator(destinationDir.path)
            }
        }

        then:
        (destinationDir / sourceFile1.name).text == sourceFile1.text
        (destinationDir / sourceFile2.name).text == sourceFile2.text
    }

    def "put() should accept a string content"() {
        given:
        def text = uuidgen()
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put text: text, into: toUnixSeparator(destinationFile.path)
            }
        }

        then:
        destinationFile.text == text
    }

    def "put() should accept a byte array content"() {
        given:
        def text = uuidgen()
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put bytes: text.bytes, into: toUnixSeparator(destinationFile.path)
            }
        }

        then:
        destinationFile.text == text
    }

    def "put() should accept an input stream"() {
        given:
        def text = uuidgen()
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                def stream = new ByteArrayInputStream(text.bytes)
                put from: stream, into: toUnixSeparator(destinationFile.path)
            }
        }

        then:
        destinationFile.text == text
    }

    def "put() should overwrite a file if destination already exists"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationFile = temporaryFolder.newFile() << uuidgen()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: sourceFile.path, into: toUnixSeparator(destinationFile.path)
            }
        }

        then:
        sourceFile.text == text
        destinationFile.text == text
    }

    def "put() should save a file of same name if destination is a directory"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: sourceFile, into: toUnixSeparator(destinationDir.path)
            }
        }

        then:
        sourceFile.text == text
        (destinationDir / sourceFile.name).text == text
    }

    def "put() should merge and overwrite a file to a directory if it is not empty"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text

        def destinationDir = temporaryFolder.newFolder()
        def destination1File = destinationDir / sourceFile.name << uuidgen()
        def destination2File = destinationDir / uuidgen() << uuidgen()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: sourceFile, into: toUnixSeparator(destinationDir.path)
            }
        }

        then:
        sourceFile.text == text
        destination1File.text == text
        destination2File.exists()
    }

    def "put() should put a whole directory if both are directories"() {
        given:
        def source1Dir = temporaryFolder.newFolder()
        def source2Dir = source1Dir / uuidgen() / DIRECTORY
        def source3Dir = source2Dir / uuidgen() / DIRECTORY

        def source1File = source1Dir / uuidgen() << uuidgen()
        def source2File = source2Dir / uuidgen() << uuidgen()

        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: source1Dir, into: toUnixSeparator(destinationDir.path)
            }
        }

        then:
        (destinationDir / source1Dir.name / source1File.name).text == source1File.text
        (destinationDir / source1Dir.name / source2Dir.name / source2File.name).text == source2File.text
        (destinationDir / source1Dir.name / source2Dir.name / source3Dir.name).list() == []
    }

    def "put() should merge and overwrite a directory to a directory if it is not empty"() {
        given:
        def source1Dir = temporaryFolder.newFolder()
        def source2Dir = source1Dir / uuidgen() / DIRECTORY
        def source3Dir = source2Dir / uuidgen() / DIRECTORY

        def source1File = source1Dir / uuidgen() << uuidgen()
        def source2File = source2Dir / uuidgen() << uuidgen()

        def destination0Dir = temporaryFolder.newFolder()
        def destination1Dir = destination0Dir / source1Dir.name / DIRECTORY
        def destination2Dir = destination1Dir / source2Dir.name / DIRECTORY
        def destination3Dir = destination2Dir / source3Dir.name / DIRECTORY

        def destination1File = destination1Dir / source1File.name << uuidgen()
        def destination2File = destination2Dir / source2File.name << uuidgen()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: source1Dir, into: toUnixSeparator(destination0Dir.path)
            }
        }

        then:
        (destination0Dir / source1Dir.name / source1File.name).text == source1File.text
        (destination0Dir / source1Dir.name / source2Dir.name / source2File.name).text == source2File.text
        (destination0Dir / source1Dir.name / source2Dir.name / source3Dir.name).list() == []

        and:
        destination1File.text == source1File.text
        destination2File.text == source2File.text
        destination3Dir.exists()
    }

    def "put() should put a whole directory even if empty"() {
        given:
        def sourceDir = temporaryFolder.newFolder()
        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: sourceDir, into: toUnixSeparator(destinationDir.path)
            }
        }

        then:
        (destinationDir / sourceDir.name).list() == []
    }

    def "put() should throw an error if source is null"() {
        given:
        def file = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: null, into: file.path
            }
        }

        then:
        AssertionError e = thrown()
        e.localizedMessage.contains 'local'
    }

    def "put() should throw an error if destination is null"() {
        given:
        def file = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put from: file.path, into: null
            }
        }

        then:
        AssertionError e = thrown()
        e.localizedMessage.contains 'remote'
    }

    @Unroll
    def "put(#key) should throw an error if into is not given"() {
        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put(argument)
            }
        }

        then:
        thrown(IllegalArgumentException)

        where:
        key      | argument
        'file'   | [from: 'somefile.txt']
        'files'  | [from: ['somefile.txt']]
        'stream' | [from: new ByteArrayInputStream([0xff, 0xff] as byte[])]
        'text'   | [text: 'something']
        'bytes'  | [bytes: [0xff, 0xff]]
    }

    def "put() should throw an error if from is not given"() {
        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                put into: 'file'
            }
        }

        then:
        thrown(IllegalArgumentException)
    }


    def "get() should accept a path string"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: destinationFile.path
            }
        }

        then:
        sourceFile.text == text
        destinationFile.text == text
    }

    def "get() should accept a file object"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: destinationFile
            }
        }

        then:
        sourceFile.text == text
        destinationFile.text == text
    }

    def "get() should accept an output stream"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def outputStream = new ByteArrayOutputStream()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: outputStream
            }
        }

        then:
        sourceFile.text == text
        outputStream.toByteArray() == text.bytes
    }

    def "get() should return content if into is not given"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text

        when:
        def content = ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path)
            }
        }

        then:
        sourceFile.text == text
        content == text
    }

    @Unroll
    def "get() should handle a binary file with #size bytes"() {
        given:
        def content = new byte[size]
        new Random().nextBytes(content)

        def sourceFile = temporaryFolder.newFile() << content
        def destinationFile = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: destinationFile.path
            }
        }

        then:
        sourceFile.bytes == content
        destinationFile.bytes == content

        where:
        size << [0, 1, 1024, 12345]
    }

    def "get() should overwrite a file if destination already exists"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationFile = temporaryFolder.newFile() << uuidgen()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: destinationFile.path
            }
        }

        then:
        sourceFile.text == text
        destinationFile.text == text
    }

    def "get() should save a file of same name if destination is a directory"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: destinationDir
            }
        }

        then:
        sourceFile.text == text

        and:
        (destinationDir / sourceFile.name).text == text
    }

    def "get() should merge and overwrite a file to a directory if it is not empty"() {
        given:
        def text = uuidgen()
        def sourceFile = temporaryFolder.newFile() << text
        def destinationDir = temporaryFolder.newFolder()
        def destination1File = destinationDir / sourceFile.name << text
        def destination2File = destinationDir / uuidgen() << text

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceFile.path), into: destinationDir
            }
        }

        then:
        sourceFile.text == text
        destination1File.text == text
        destination2File.exists()
    }

    def "get() should get a whole directory if source is a directory"() {
        given:
        def source1Dir = temporaryFolder.newFolder()
        def source2Dir = source1Dir / uuidgen() / DIRECTORY
        def source3Dir = source2Dir / uuidgen() / DIRECTORY

        def source1File = source1Dir / uuidgen() << uuidgen()
        def source2File = source2Dir / uuidgen() << uuidgen()

        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(source1Dir.path), into: destinationDir
            }
        }

        then:
        (destinationDir / source1Dir.name / source1File.name).text == source1File.text
        (destinationDir / source1Dir.name / source2Dir.name / source2File.name).text == source2File.text
        (destinationDir / source1Dir.name / source2Dir.name / source3Dir.name).list() == []
    }

    def "get() should merge and overwrite a directory to a directory if it is not empty"() {
        given:
        def source1Dir = temporaryFolder.newFolder()
        def source2Dir = source1Dir / uuidgen() / DIRECTORY
        def source3Dir = source2Dir / uuidgen() / DIRECTORY

        def source1File = source1Dir / uuidgen() << uuidgen()
        def source2File = source2Dir / uuidgen() << uuidgen()

        def destination0Dir = temporaryFolder.newFolder()
        def destination1Dir = destination0Dir / source1Dir.name / DIRECTORY
        def destination2Dir = destination1Dir / source2Dir.name / DIRECTORY
        def destination3Dir = destination2Dir / source3Dir.name / DIRECTORY

        def destination1File = destination1Dir / source1File.name << uuidgen()
        def destination2File = destination2Dir / source2File.name << uuidgen()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(source1Dir.path), into: destination0Dir
            }
        }

        then:
        (destination0Dir / source1Dir.name / source1File.name).text == source1File.text
        (destination0Dir / source1Dir.name / source2Dir.name / source2File.name).text == source2File.text
        (destination0Dir / source1Dir.name / source2Dir.name / source3Dir.name).list() == []

        and:
        destination1File.text == source1File.text
        destination2File.text == source2File.text
        destination3Dir.exists()
    }

    def "get() should get a whole directory even if empty"() {
        given:
        def sourceDir = temporaryFolder.newFolder()
        def destinationDir = temporaryFolder.newFolder()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: toUnixSeparator(sourceDir.path), into: destinationDir
            }
        }

        then:
        (destinationDir / sourceDir.name).list() == []
    }

    def "get() should throw an error if source is null"() {
        given:
        def file = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: null, into: file.path
            }
        }

        then:
        AssertionError e = thrown()
        e.localizedMessage.contains 'remote'
    }

    def "get() should throw an error if destination is null string"() {
        given:
        def file = temporaryFolder.newFile()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get from: file.path, into: ''
            }
        }

        then:
        AssertionError e = thrown()
        e.localizedMessage.contains 'local'
    }

    def "get() should throw an error if from is not given"() {
        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                get into: 'somefile'
            }
        }

        then:
        thrown(IllegalArgumentException)
    }

}
