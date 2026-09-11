package io.github.matijaradovic.spona.storage;

import java.time.Duration;

/** The object store, in the two shapes its two buckets allow */
public interface StorageService {

    /** How long a signed CV link stays valid. Long enough to open the file, not to forward it. */
    Duration CV_URL_TTL = Duration.ofMinutes(5);

    /** returns public url*/
    String putAvatar(byte[] content, String contentType);

    /** returns a cv key */
    String putCv(byte[] content, String contentType);

    /** GET req on a CV key, signed for the public endpoint and valid for {@link #CV_URL_TTL}. */
    String presignCv(String objectKey);
}
