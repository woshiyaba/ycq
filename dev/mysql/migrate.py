"""Apply the additive SQL files using YCQ_DB_HOST/USER/PASSWORD (requires PyMySQL)."""
import argparse
import os
from pathlib import Path

import pymysql
from pymysql.constants import CLIENT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="execute migrations; otherwise list them")
    args = parser.parse_args()
    scripts = sorted((Path(__file__).parent / "migrations").glob("*.sql"))
    if not args.apply:
        for script in scripts:
            print(script.name)
        return
    connection = pymysql.connect(
        host=os.environ["YCQ_DB_HOST"], port=int(os.environ.get("YCQ_DB_PORT", "3306")),
        user=os.environ["YCQ_DB_USER"], password=os.environ["YCQ_DB_PASSWORD"],
        charset="utf8mb4", autocommit=True, connect_timeout=10, read_timeout=60,
        client_flag=CLIENT.MULTI_STATEMENTS,
    )
    try:
        with connection.cursor() as cursor:
            for script in scripts:
                cursor.execute(script.read_text(encoding="utf-8"))
                while cursor.nextset():
                    pass
                print("Applied:", script.name)
            cursor.execute("SELECT table_schema,table_name FROM information_schema.tables "
                           "WHERE table_schema IN ('user_service','goods_service','im_service') "
                           "ORDER BY table_schema,table_name")
            for schema, table in cursor.fetchall():
                print(schema + "." + table)
    finally:
        connection.close()


if __name__ == "__main__":
    main()
