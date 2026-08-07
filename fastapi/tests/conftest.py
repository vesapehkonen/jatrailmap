import mongomock
import pytest


@pytest.fixture
def database():
    return mongomock.MongoClient().jatrail
